package com.samuel.flashbooking.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyEntity {
    @Id
    public UUID id;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 160)
    public String key;
    @Column(name = "request_hash", nullable = false, length = 64)
    public String requestHash;
    @Column(name = "reservation_id", nullable = false)
    public UUID reservationId;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    protected IdempotencyEntity() {
    }

    public IdempotencyEntity(UUID id, String key, String requestHash, UUID reservationId, Instant createdAt) {
        this.id = id;
        this.key = key;
        this.requestHash = requestHash;
        this.reservationId = reservationId;
        this.createdAt = createdAt;
    }
}
