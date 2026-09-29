package com.samuel.flashbooking.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Event(UUID id, String name, Instant startsAt, int capacity, int availableTickets, Instant createdAt) {
    public Event {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(startsAt);
        Objects.requireNonNull(createdAt);
        if (name.isBlank() || capacity < 1 || availableTickets < 0 || availableTickets > capacity) {
            throw new IllegalArgumentException("Invalid event state");
        }
    }

    public static Event create(String name, Instant startsAt, int capacity, Instant now) {
        return new Event(UUID.randomUUID(), name, startsAt, capacity, capacity, now);
    }
}
