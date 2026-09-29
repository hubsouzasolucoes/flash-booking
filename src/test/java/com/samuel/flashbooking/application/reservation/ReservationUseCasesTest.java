package com.samuel.flashbooking.application.reservation;

import com.samuel.flashbooking.application.ApplicationException;
import com.samuel.flashbooking.application.DomainEventOutbox;
import com.samuel.flashbooking.application.event.EventRepository;
import com.samuel.flashbooking.domain.reservation.Reservation;
import com.samuel.flashbooking.domain.reservation.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import static com.samuel.flashbooking.application.ApplicationException.ErrorCode.IDEMPOTENCY_CONFLICT;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ReservationUseCasesTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final EventRepository events = mock(EventRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final IdempotencyStore idempotency = mock(IdempotencyStore.class);
    private final DomainEventOutbox outbox = mock(DomainEventOutbox.class);
    private ReservationUseCases useCases;

    @BeforeEach
    void setUp() {
        useCases = new ReservationUseCases(events, reservations, idempotency, outbox,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(10), new SimpleMeterRegistry());
    }

    @Test
    void createsReservationOnlyAfterAtomicCapacityAcquisition() {
        UUID eventId = UUID.randomUUID();
        when(events.existsById(eventId)).thenReturn(true);
        when(events.reserveCapacity(eventId, 2)).thenReturn(Optional.of(new EventRepository.CapacityState(8, 2)));
        when(reservations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation result = useCases.create(eventId, 2, "request-1");

        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        verify(idempotency).lock("request-1");
        verify(idempotency).save(eq("request-1"), anyString(), eq(result.id()), eq(NOW));
        verify(outbox).append(any());
    }

    @Test
    void sameKeyAndPayloadReturnsOriginalWithoutAcquiringCapacity() {
        UUID eventId = UUID.randomUUID();
        Reservation original = Reservation.create(eventId, 2, NOW.plusSeconds(600), NOW);
        when(idempotency.find("request-1")).thenReturn(Optional.of(
                new IdempotencyStore.Record(RequestFingerprint.reservation(eventId, 2), original.id())));
        when(reservations.findById(original.id())).thenReturn(Optional.of(original));

        assertThat(useCases.create(eventId, 2, "request-1")).isSameAs(original);
        verify(events, never()).reserveCapacity(any(), anyInt());
        verify(outbox, never()).append(any());
    }

    @Test
    void sameKeyWithDifferentPayloadIsAConflict() {
        UUID eventId = UUID.randomUUID();
        when(idempotency.find("request-1")).thenReturn(Optional.of(new IdempotencyStore.Record("different", UUID.randomUUID())));

        ApplicationException exception = catchThrowableOfType(
                () -> useCases.create(eventId, 2, "request-1"), ApplicationException.class);
        assertThat(exception.code()).isEqualTo(IDEMPOTENCY_CONFLICT);
        verify(events, never()).reserveCapacity(any(), anyInt());
    }

    @Test
    void cancellationRetryDoesNotReleaseCapacityOrEmitAgain() {
        UUID id = UUID.randomUUID();
        Reservation reservation = Reservation.create(UUID.randomUUID(), 2, NOW.plusSeconds(600), NOW);
        reservation.cancel(NOW);
        when(reservations.findByIdForUpdate(id)).thenReturn(Optional.of(reservation));

        assertThat(useCases.cancel(id).status()).isEqualTo(ReservationStatus.CANCELLED);
        verify(events, never()).releaseCapacity(any(), anyInt());
        verify(outbox, never()).append(any());
    }

}
