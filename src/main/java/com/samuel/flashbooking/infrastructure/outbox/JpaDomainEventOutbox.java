package com.samuel.flashbooking.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.flashbooking.application.DomainEventOutbox;
import com.samuel.flashbooking.domain.shared.DomainEvent;
import com.samuel.flashbooking.infrastructure.persistence.entity.OutboxEventEntity;
import com.samuel.flashbooking.infrastructure.persistence.repository.JpaOutboxRepository;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.UUID;

@Component
public class JpaDomainEventOutbox implements DomainEventOutbox {
    private final JpaOutboxRepository repository;
    private final ObjectMapper objectMapper;
    public JpaDomainEventOutbox(JpaOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository; this.objectMapper = objectMapper;
    }

    @Override
    public void append(DomainEvent event) {
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", UUID.randomUUID());
        envelope.put("aggregateId", event.aggregateId());
        envelope.put("aggregateType", event.aggregateType());
        envelope.put("eventType", event.eventType());
        envelope.put("occurredAt", event.occurredAt());
        envelope.put("data", event.data());
        try {
            repository.save(new OutboxEventEntity((UUID) envelope.get("eventId"), event.aggregateId(),
                    event.aggregateType(), event.eventType(), objectMapper.writeValueAsString(envelope),
                    event.occurredAt()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize domain event", exception);
        }
    }
}
