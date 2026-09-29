package com.samuel.flashbooking.domain.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class EventTest {
    @Test
    void newEventStartsWithAllCapacityAvailable() {
        Event event = Event.create("Conference", Instant.parse("2027-01-01T00:00:00Z"), 50,
                Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(event.availableTickets()).isEqualTo(50);
    }

    @Test
    void rejectsInvalidAvailability() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Event(java.util.UUID.randomUUID(), "Event",
                Instant.now(), 10, 11, 1, Instant.now()));
    }
}
