package com.samuel.flashbooking.infrastructure.persistence.repository;

import com.samuel.flashbooking.infrastructure.persistence.entity.ReservationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaReservationRepository extends JpaRepository<ReservationEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from ReservationEntity reservation where reservation.id = :id")
    Optional<ReservationEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT * FROM reservations WHERE status = 'PENDING' AND expires_at <= :now " +
            "ORDER BY expires_at FOR UPDATE SKIP LOCKED LIMIT :limit", nativeQuery = true)
    List<ReservationEntity> findExpiredForUpdate(@Param("now") Instant now, @Param("limit") int limit);
}
