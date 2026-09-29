package com.samuel.flashbooking.infrastructure.persistence.repository;

import com.samuel.flashbooking.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface JpaOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {
    @Query(value = "SELECT candidate.* FROM outbox_events candidate WHERE " +
            "((candidate.status = 'PENDING' AND candidate.next_attempt_at <= :now) " +
            "OR (candidate.status = 'PROCESSING' AND candidate.locked_until < :now)) " +
            "AND NOT EXISTS (SELECT 1 FROM outbox_events predecessor WHERE predecessor.aggregate_id = candidate.aggregate_id " +
            "AND predecessor.published_at IS NULL AND predecessor.aggregate_version < candidate.aggregate_version) " +
            "ORDER BY candidate.occurred_at LIMIT 100 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEventEntity> findClaimableForUpdate(@org.springframework.data.repository.query.Param("now") java.time.Instant now);

    long countByStatus(String status);
    long countByStatusIn(java.util.Collection<String> statuses);
}
