package com.samuel.flashbooking.infrastructure.messaging;

import com.samuel.flashbooking.infrastructure.persistence.entity.OutboxEventEntity;
import com.samuel.flashbooking.infrastructure.persistence.repository.JpaOutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class OutboxClaims {
    private final JpaOutboxRepository repository;

    public OutboxClaims(JpaOutboxRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEventEntity> claim(Instant now) {
        var events = repository.findClaimableForUpdate(now);
        events.forEach(event -> {
            event.status = "PROCESSING";
            event.lockedUntil = now.plus(Duration.ofMinutes(1));
        });
        return events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void published(UUID id, Instant now) { repository.findById(id).orElseThrow().markPublished(now); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(UUID id, Instant retryAt, String error) {
        repository.findById(id).orElseThrow().markFailed(retryAt, error);
    }
}
