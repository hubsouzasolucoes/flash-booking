package com.samuel.flashbooking.infrastructure.persistence.adapter;

import com.samuel.flashbooking.application.reservation.IdempotencyStore;
import com.samuel.flashbooking.infrastructure.persistence.entity.IdempotencyEntity;
import com.samuel.flashbooking.infrastructure.persistence.repository.JpaIdempotencyRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresIdempotencyStore implements IdempotencyStore {
    private final JpaIdempotencyRepository repository;
    private final EntityManager entityManager;

    public PostgresIdempotencyStore(JpaIdempotencyRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public void lock(String key) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .setParameter("key", key).getSingleResult();
    }

    @Override
    public Optional<Record> find(String key) {
        return repository.findByKey(key).map(value -> new Record(value.requestHash, value.reservationId));
    }

    @Override
    public void save(String key, String requestHash, UUID reservationId, Instant createdAt) {
        repository.save(new IdempotencyEntity(UUID.randomUUID(), key, requestHash, reservationId, createdAt));
    }
}
