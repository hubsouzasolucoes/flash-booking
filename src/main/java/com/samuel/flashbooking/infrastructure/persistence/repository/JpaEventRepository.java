package com.samuel.flashbooking.infrastructure.persistence.repository;

import com.samuel.flashbooking.infrastructure.persistence.entity.EventEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface JpaEventRepository extends JpaRepository<EventEntity, UUID> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE events SET available_tickets = available_tickets - :quantity " +
            "WHERE id = :id AND available_tickets >= :quantity", nativeQuery = true)
    int reserve(@Param("id") UUID id, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE events SET available_tickets = available_tickets + :quantity " +
            "WHERE id = :id AND available_tickets + :quantity <= capacity", nativeQuery = true)
    int release(@Param("id") UUID id, @Param("quantity") int quantity);
}
