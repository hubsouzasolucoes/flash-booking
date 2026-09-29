package com.samuel.flashbooking.infrastructure.persistence.entity;

import com.samuel.flashbooking.domain.reservation.ReservationStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class ReservationEntity {
    @Id
    public UUID id;
    @Column(name = "event_id", nullable = false)
    public UUID eventId;
    @Column(nullable = false)
    public int quantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public ReservationStatus status;
    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    protected ReservationEntity() {
    }

    public ReservationEntity(UUID id, UUID eventId, int quantity, ReservationStatus status, Instant expiresAt,
                             Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.eventId = eventId;
        this.quantity = quantity;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
