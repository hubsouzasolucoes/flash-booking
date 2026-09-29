package com.samuel.flashbooking.repository;

import com.samuel.flashbooking.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    @Query(value = "SELECT * FROM reservations WHERE status='PENDING' AND expires_at<=:now ORDER BY expires_at FOR UPDATE SKIP LOCKED LIMIT 100", nativeQuery = true)
    List<Reservation> findExpiredForUpdate(@Param("now") Instant now);
}
