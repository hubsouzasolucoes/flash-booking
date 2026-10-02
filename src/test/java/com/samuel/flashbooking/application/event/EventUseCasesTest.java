package com.samuel.flashbooking.application.event;

import com.samuel.flashbooking.application.DomainEventOutbox;
import com.samuel.flashbooking.domain.event.Event;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventUseCasesTest {
    @Test
    void fallsBackToAuthoritativeEventWhileProjectionIsNotReady() {
        EventRepository events = mock(EventRepository.class);
        EventAvailability availability = mock(EventAvailability.class);
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Event event = new Event(id, "Show", createdAt.plusSeconds(3600), 100, 99, 2, createdAt);
        when(availability.findById(id)).thenReturn(Optional.empty());
        when(events.findById(id)).thenReturn(Optional.of(event));
        EventUseCases useCases = new EventUseCases(events, mock(DomainEventOutbox.class),
                Clock.fixed(createdAt, ZoneOffset.UTC), availability);

        EventAvailability.View result = useCases.getAvailability(id);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.availableTickets()).isEqualTo(99);
        assertThat(result.lastEventVersion()).isEqualTo(2);
    }
}
