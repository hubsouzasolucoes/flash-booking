package com.samuel.flashbooking.infrastructure.persistence.adapter;

import com.samuel.flashbooking.application.event.EventRepository;
import com.samuel.flashbooking.domain.event.Event;
import com.samuel.flashbooking.infrastructure.persistence.entity.EventEntity;
import com.samuel.flashbooking.infrastructure.persistence.repository.JpaEventRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.EntityManager;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresEventRepository implements EventRepository {
    private final JpaEventRepository repository;
    private final EntityManager entityManager;

    public PostgresEventRepository(JpaEventRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public Event save(Event event) {
        return domain(repository.save(entity(event)));
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return repository.findById(id).map(this::domain);
    }

    @Override
    public Optional<CapacityState> reserveCapacity(UUID id, int quantity) {
        return updateCapacity("available_tickets - :quantity", "available_tickets >= :quantity", id, quantity);
    }

    @Override
    public CapacityState releaseCapacity(UUID id, int quantity) {
        return updateCapacity("available_tickets + :quantity", "available_tickets + :quantity <= capacity", id, quantity)
                .orElseThrow(() -> new IllegalStateException("Capacity release invariant violated"));
    }

    private EventEntity entity(Event e) {
        return new EventEntity(e.id(), e.name(), e.startsAt(), e.capacity(), e.availableTickets(), e.version(), e.createdAt());
    }

    private Event domain(EventEntity e) {
        return new Event(e.id, e.name, e.startsAt, e.capacity, e.availableTickets, e.version, e.createdAt);
    }

    @SuppressWarnings("unchecked")
    private Optional<CapacityState> updateCapacity(String expression, String condition, UUID id, int quantity) {
        var rows = entityManager.createNativeQuery("UPDATE events SET available_tickets = " + expression +
                        ", version = version + 1 WHERE id = :id AND " + condition +
                        " RETURNING available_tickets, version")
                .setParameter("id", id).setParameter("quantity", quantity).getResultList();
        if (rows.isEmpty()) return Optional.empty();
        Object[] row = (Object[]) rows.getFirst();
        return Optional.of(new CapacityState(((Number) row[0]).intValue(), ((Number) row[1]).longValue()));
    }
}
