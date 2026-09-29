package com.samuel.flashbooking.repository;

import com.samuel.flashbooking.domain.OutboxEvent;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = "SELECT * FROM outbox_events WHERE published_at IS NULL ORDER BY occurred_at LIMIT 100 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> unpublished();
}
