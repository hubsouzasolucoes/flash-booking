package com.samuel.flashbooking.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {
    @Id public UUID id;
    @Column(name = "aggregate_id", nullable = false) public UUID aggregateId;
    @Column(name = "aggregate_type", nullable = false, length = 80) public String aggregateType;
    @Column(name = "aggregate_version", nullable = false) public long aggregateVersion;
    @Column(name = "event_type", nullable = false, length = 100) public String eventType;
    @Column(name = "event_version", nullable = false) public int eventVersion;
    @Column(nullable = false, columnDefinition = "text") public String payload;
    @Column(name = "occurred_at", nullable = false) public Instant occurredAt;
    @Column(name = "published_at") public Instant publishedAt;
    @Column(nullable = false, length = 20) public String status;
    @Column(nullable = false) public int attempts;
    @Column(name = "next_attempt_at", nullable = false) public Instant nextAttemptAt;
    @Column(name = "locked_until") public Instant lockedUntil;
    @Column(name = "last_error", length = 500) public String lastError;

    protected OutboxEventEntity() {}
    public OutboxEventEntity(UUID id, UUID aggregateId, String aggregateType, long aggregateVersion,
                             String eventType, int eventVersion,
                             String payload, Instant occurredAt) {
        this.id = id; this.aggregateId = aggregateId; this.aggregateType = aggregateType; this.aggregateVersion = aggregateVersion;
        this.eventType = eventType; this.eventVersion = eventVersion; this.payload = payload; this.occurredAt = occurredAt;
        this.status = "PENDING"; this.nextAttemptAt = occurredAt;
    }
    public void markPublished(Instant now) { publishedAt = now; status = "PUBLISHED"; lockedUntil = null; lastError = null; }
    public void markFailed(Instant retryAt, String error) {
        attempts++; status = "PENDING"; nextAttemptAt = retryAt; lockedUntil = null;
        lastError = error == null ? "Unknown publication failure" : error.substring(0, Math.min(error.length(), 500));
    }
}
