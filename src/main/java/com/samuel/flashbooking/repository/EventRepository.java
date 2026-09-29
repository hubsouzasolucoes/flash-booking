package com.samuel.flashbooking.repository;

import com.samuel.flashbooking.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    @Modifying
    @Query(value = "UPDATE events SET available_tickets=available_tickets-:q WHERE id=:id AND available_tickets>=:q", nativeQuery = true)
    int reserve(@Param("id") UUID id, @Param("q") int quantity);

    @Modifying
    @Query(value = "UPDATE events SET available_tickets=LEAST(capacity,available_tickets+:q) WHERE id=:id", nativeQuery = true)
    int release(@Param("id") UUID id, @Param("q") int quantity);
}
