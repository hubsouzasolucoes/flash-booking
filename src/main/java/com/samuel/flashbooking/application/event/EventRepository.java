package com.samuel.flashbooking.application.event;

import com.samuel.flashbooking.domain.event.Event;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository {
    Event save(Event event);
    Optional<Event> findById(UUID id);
    boolean existsById(UUID id);
    boolean reserveCapacity(UUID id, int quantity);
    void releaseCapacity(UUID id, int quantity);
}
