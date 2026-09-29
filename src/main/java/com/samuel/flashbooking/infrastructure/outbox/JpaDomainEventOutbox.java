package com.samuel.flashbooking.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.flashbooking.application.DomainEventOutbox;
import com.samuel.flashbooking.domain.shared.DomainEvent;
import com.samuel.flashbooking.infrastructure.persistence.entity.OutboxEventEntity;
import com.samuel.flashbooking.infrastructure.persistence.repository.JpaOutboxRepository;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JpaDomainEventOutbox implements DomainEventOutbox {
    private static final Logger log = LoggerFactory.getLogger(JpaDomainEventOutbox.class);
    private final JpaOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public JpaDomainEventOutbox(JpaOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(DomainEvent event) {
        try {
            repository.save(new OutboxEventEntity(event.eventId(), event.aggregateId(),
                    event.aggregateType(), event.aggregateVersion(), event.eventType(), event.eventVersion(), objectMapper.writeValueAsString(event),
                    event.occurredAt()));
            log.debug("event=outbox_created eventId={} aggregateId={} eventType={} correlationId={}",
                    event.eventId(), event.aggregateId(), event.eventType(), event.correlationId());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize domain event", exception);
        }
    }
}
