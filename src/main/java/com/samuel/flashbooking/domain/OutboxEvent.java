package com.samuel.flashbooking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    private UUID id;
    @Column(name = "aggregate_id")
    private UUID aggregateId;
    @Column(name = "aggregate_type")
    private String aggregateType;
    @Column(name = "event_type")
    private String eventType;
    @Column(columnDefinition = "text")
    private String payload;
    @Column(name = "occurred_at")
    private Instant occurredAt;
    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID aggregateId, String aggregateType, String eventType, String payload, Instant now) {
        this.id = UUID.randomUUID();
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void published(Instant now) {
        publishedAt = now;
    }
}
