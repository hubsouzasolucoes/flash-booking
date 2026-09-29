package com.samuel.flashbooking.domain.shared;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DomainEvent(UUID eventId, UUID aggregateId, String aggregateType, long aggregateVersion,
                          String eventType, int eventVersion, UUID correlationId, UUID causationId,
                          Map<String, Object> payload, Instant occurredAt) {
    public DomainEvent {
        if (aggregateVersion < 1 || eventVersion < 1) throw new IllegalArgumentException("Versions must be positive");
        dataRequired(eventId, aggregateId, aggregateType, eventType, correlationId, payload, occurredAt);
    }

    private static void dataRequired(Object... values) {
        for (Object value : values) java.util.Objects.requireNonNull(value);
    }
}
