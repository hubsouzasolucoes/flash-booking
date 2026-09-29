package com.samuel.flashbooking.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
public class EventEntity {
    @Id public UUID id;
    @Column(nullable = false, length = 160) public String name;
    @Column(name = "starts_at", nullable = false) public Instant startsAt;
    @Column(nullable = false) public int capacity;
    @Column(name = "available_tickets", nullable = false) public int availableTickets;
    @Column(name = "created_at", nullable = false) public Instant createdAt;

    protected EventEntity() {}

    public EventEntity(UUID id, String name, Instant startsAt, int capacity, int availableTickets, Instant createdAt) {
        this.id = id; this.name = name; this.startsAt = startsAt; this.capacity = capacity;
        this.availableTickets = availableTickets; this.createdAt = createdAt;
    }
}
