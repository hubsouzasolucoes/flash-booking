package com.samuel.flashbooking.application.reservation;

import com.samuel.flashbooking.application.ApplicationException;
import com.samuel.flashbooking.application.DomainEventOutbox;
import com.samuel.flashbooking.application.CorrelationIds;
import com.samuel.flashbooking.application.event.EventRepository;
import com.samuel.flashbooking.domain.reservation.Reservation;
import com.samuel.flashbooking.domain.reservation.ReservationStatus;
import com.samuel.flashbooking.domain.shared.DomainEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.samuel.flashbooking.application.ApplicationException.ErrorCode.*;

@Service
public class ReservationUseCases {
    private static final int EXPIRATION_BATCH_SIZE = 100;
    private final EventRepository events;
    private final ReservationRepository reservations;
    private final IdempotencyStore idempotency;
    private final DomainEventOutbox outbox;
    private final Clock clock;
    private final Duration ttl;
    private final Counter created;
    private final Counter capacityRejected;
    private final Counter replayed;
    private final Counter cancelled;
    private final Counter expiredCounter;
    private final Counter idempotencyCreated;
    private final Counter idempotencyConflict;
    private final Counter idempotencyReplay;
    private final Counter idempotencyRejected;
    private final Timer duration;
    private final Timer expirationBatchDuration;

    public ReservationUseCases(EventRepository events, ReservationRepository reservations,
                               IdempotencyStore idempotency, DomainEventOutbox outbox, Clock clock,
                               @Value("${app.reservation-ttl}") Duration ttl, MeterRegistry metrics) {
        this.events = events;
        this.reservations = reservations;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.clock = clock;
        this.ttl = ttl;
        this.created = metrics.counter("booking.reservation.created");
        this.capacityRejected = metrics.counter("booking.reservation.rejected", "reason", "insufficient_capacity");
        this.replayed = metrics.counter("booking.reservation.idempotent.replay");
        this.cancelled = metrics.counter("booking.reservation.cancelled");
        this.expiredCounter = metrics.counter("booking.reservation.expired");
        this.idempotencyCreated = metrics.counter("booking.idempotency.created");
        this.idempotencyConflict = metrics.counter("booking.idempotency.conflict");
        this.idempotencyReplay = metrics.counter("booking.idempotency.replay");
        this.idempotencyRejected = metrics.counter("booking.reservation.rejected", "reason", "idempotency_conflict");
        this.duration = metrics.timer("booking.reservation.duration");
        this.expirationBatchDuration = metrics.timer("booking.expiration.batch.duration");
    }

    @Transactional
    public Reservation create(UUID eventId, int quantity, String key) {
        return duration.record(() -> createReservation(eventId, quantity, key));
    }

    private Reservation createReservation(UUID eventId, int quantity, String key) {
        String requestHash = RequestFingerprint.reservation(eventId, quantity);
        // A PostgreSQL transaction-scoped advisory lock serializes one key across every API instance.
        idempotency.lock(key);
        var previous = idempotency.find(key);
        if (previous.isPresent()) {
            if (!previous.get().requestHash().equals(requestHash)) {
                idempotencyConflict.increment();
                idempotencyRejected.increment();
                throw new ApplicationException(IDEMPOTENCY_CONFLICT,
                        "Idempotency key was already used with a different request");
            }
            replayed.increment();
            idempotencyReplay.increment();
            return getRequired(previous.get().reservationId());
        }
        if (!events.existsById(eventId)) {
            throw new ApplicationException(EVENT_NOT_FOUND, "Event not found");
        }
        var capacity = events.reserveCapacity(eventId, quantity);
        if (capacity.isEmpty()) {
            capacityRejected.increment();
            throw new ApplicationException(INSUFFICIENT_CAPACITY, "Not enough tickets available");
        }

        Instant now = clock.instant();
        Reservation reservation = reservations.save(Reservation.create(eventId, quantity, now.plus(ttl), now));
        idempotency.save(key, requestHash, reservation.id(), now);
        idempotencyCreated.increment();
        outbox.append(event("ReservationCreated", reservation, capacity.orElseThrow(), now));
        created.increment();
        return reservation;
    }

    @Transactional(readOnly = true)
    public Reservation get(UUID id) {
        return getRequired(id);
    }

    @Transactional
    public Reservation cancel(UUID id) {
        Reservation reservation = reservations.findByIdForUpdate(id)
                .orElseThrow(() -> new ApplicationException(RESERVATION_NOT_FOUND, "Reservation not found"));
        Instant now = clock.instant();
        if (reservation.status() == ReservationStatus.CANCELLED) {
            return reservation; // DELETE retry: the first transaction already released capacity.
        }
        if (!reservation.cancel(now)) {
            throw new ApplicationException(RESERVATION_NOT_CANCELLABLE,
                    "Only pending reservations can be cancelled");
        }
        reservations.save(reservation);
        var capacity = events.releaseCapacity(reservation.eventId(), reservation.quantity());
        outbox.append(event("ReservationCancelled", reservation, capacity, now));
        cancelled.increment();
        return reservation;
    }

    @Transactional
    public int expireBatch() {
        return expirationBatchDuration.record(this::expireReservations);
    }

    private int expireReservations() {
        Instant now = clock.instant();
        int expired = 0;
        for (Reservation reservation : reservations.findExpiredForUpdate(now, EXPIRATION_BATCH_SIZE)) {
            if (reservation.expire(now)) {
                reservations.save(reservation);
                var capacity = events.releaseCapacity(reservation.eventId(), reservation.quantity());
                outbox.append(event("ReservationExpired", reservation, capacity, now));
                expiredCounter.increment();
                expired++;
            }
        }
        return expired;
    }

    private Reservation getRequired(UUID id) {
        return reservations.findById(id)
                .orElseThrow(() -> new ApplicationException(RESERVATION_NOT_FOUND, "Reservation not found"));
    }

    private DomainEvent event(String type, Reservation reservation, EventRepository.CapacityState capacity, Instant now) {
        UUID eventId = UUID.randomUUID();
        return new DomainEvent(eventId, reservation.eventId(), "Event", capacity.version(), type, 1,
                CorrelationIds.currentOrNew(), null,
                Map.of("reservationId", reservation.id(), "eventId", reservation.eventId(),
                        "quantity", reservation.quantity(), "status", reservation.status(),
                        "availableTickets", capacity.availableTickets()), now);
    }

}
