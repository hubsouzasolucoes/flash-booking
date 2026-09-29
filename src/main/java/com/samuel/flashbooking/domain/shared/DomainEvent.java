package com.samuel.flashbooking.domain.shared;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DomainEvent(UUID aggregateId, String aggregateType, String eventType,
                          Map<String, Object> data, Instant occurredAt) {
}
