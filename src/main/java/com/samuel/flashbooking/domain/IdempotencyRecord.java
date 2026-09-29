package com.samuel.flashbooking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
    @Id
    private UUID id;
    @Column(name = "idempotency_key", unique = true)
    private String key;
    @Column(name = "request_hash")
    private String requestHash;
    @Column(name = "reservation_id")
    private UUID reservationId;
    @Column(name = "created_at")
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(UUID id, String key, String hash, UUID reservationId, Instant now) {
        this.id = id;
        this.key = key;
        this.requestHash = hash;
        this.reservationId = reservationId;
        this.createdAt = now;
    }

    public String getKey() {
        return key;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getReservationId() {
        return reservationId;
    }
}
