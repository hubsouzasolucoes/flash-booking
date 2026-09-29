package com.samuel.flashbooking.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    private UUID id;
    @Column(name = "event_id")
    private UUID eventId;
    private int quantity;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Reservation() {
    }

    public Reservation(UUID id, UUID eventId, int quantity, Instant expiresAt, Instant now) {
        this.id = id;
        this.eventId = eventId;
        this.quantity = quantity;
        this.status = ReservationStatus.PENDING;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        status = ReservationStatus.CANCELLED;
        updatedAt = now;
    }

    public void expire(Instant now) {
        status = ReservationStatus.EXPIRED;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
