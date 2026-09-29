package com.samuel.flashbooking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {
    @Id
    private UUID id;
    private String name;
    @Column(name = "starts_at")
    private Instant startsAt;
    private int capacity;
    @Column(name = "available_tickets")
    private int availableTickets;
    @Column(name = "created_at")
    private Instant createdAt;

    protected Event() {
    }

    public Event(UUID id, String name, Instant startsAt, int capacity, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.startsAt = startsAt;
        this.capacity = capacity;
        this.availableTickets = capacity;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getAvailableTickets() {
        return availableTickets;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
