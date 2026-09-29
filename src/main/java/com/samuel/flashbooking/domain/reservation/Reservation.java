package com.samuel.flashbooking.domain.reservation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Reservation {
    private final UUID id;
    private final UUID eventId;
    private final int quantity;
    private ReservationStatus status;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public Reservation(UUID id, UUID eventId, int quantity, ReservationStatus status, Instant expiresAt,
                       Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.eventId = Objects.requireNonNull(eventId);
        if (quantity < 1) throw new IllegalArgumentException("Quantity must be positive");
        this.quantity = quantity;
        this.status = Objects.requireNonNull(status);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static Reservation create(UUID eventId, int quantity, Instant expiresAt, Instant now) {
        return new Reservation(UUID.randomUUID(), eventId, quantity, ReservationStatus.PENDING, expiresAt, now, now);
    }

    public boolean cancel(Instant now) {
        if (status != ReservationStatus.PENDING) return false;
        status = ReservationStatus.CANCELLED;
        updatedAt = now;
        return true;
    }

    public boolean expire(Instant now) {
        if (status != ReservationStatus.PENDING || expiresAt.isAfter(now)) return false;
        status = ReservationStatus.EXPIRED;
        updatedAt = now;
        return true;
    }

    public UUID id() { return id; }
    public UUID eventId() { return eventId; }
    public int quantity() { return quantity; }
    public ReservationStatus status() { return status; }
    public Instant expiresAt() { return expiresAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
