package com.samuel.flashbooking.infrastructure.persistence.adapter;

import com.samuel.flashbooking.application.event.EventRepository;
import com.samuel.flashbooking.domain.event.Event;
import com.samuel.flashbooking.infrastructure.persistence.entity.EventEntity;
import com.samuel.flashbooking.infrastructure.persistence.repository.JpaEventRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresEventRepository implements EventRepository {
    private final JpaEventRepository repository;
    public PostgresEventRepository(JpaEventRepository repository) { this.repository = repository; }

    @Override public Event save(Event event) { return domain(repository.save(entity(event))); }
    @Override public Optional<Event> findById(UUID id) { return repository.findById(id).map(this::domain); }
    @Override public boolean existsById(UUID id) { return repository.existsById(id); }
    @Override public boolean reserveCapacity(UUID id, int quantity) { return repository.reserve(id, quantity) == 1; }
    @Override public void releaseCapacity(UUID id, int quantity) {
        if (repository.release(id, quantity) != 1) throw new IllegalStateException("Capacity release invariant violated");
    }

    private EventEntity entity(Event e) {
        return new EventEntity(e.id(), e.name(), e.startsAt(), e.capacity(), e.availableTickets(), e.createdAt());
    }
    private Event domain(EventEntity e) {
        return new Event(e.id, e.name, e.startsAt, e.capacity, e.availableTickets, e.createdAt);
    }
}
