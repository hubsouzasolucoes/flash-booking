package com.samuel.flashbooking.infrastructure.persistence.repository;

import com.samuel.flashbooking.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface JpaOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {
    @Query(value = "SELECT * FROM outbox_events WHERE published_at IS NULL " +
            "ORDER BY occurred_at LIMIT 100 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEventEntity> findUnpublishedForUpdate();
}
