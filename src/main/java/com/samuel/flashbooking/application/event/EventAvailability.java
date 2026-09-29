package com.samuel.flashbooking.application.event;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EventAvailability {
    Optional<View> findById(UUID id);

    record View(UUID id, String name, Instant startsAt, int capacity, int availableTickets, Instant createdAt,
                Instant updatedAt, long lastEventVersion) {}
}
