package com.samuel.flashbooking.application.event;

import com.samuel.flashbooking.domain.event.Event;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository {
    Event save(Event event);

    boolean existsById(UUID id);

    Optional<CapacityState> reserveCapacity(UUID id, int quantity);

    CapacityState releaseCapacity(UUID id, int quantity);

    record CapacityState(int availableTickets, long version) {
    }
}
