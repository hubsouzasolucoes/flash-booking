package com.samuel.flashbooking.application.reservation;

import com.samuel.flashbooking.domain.reservation.Reservation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {
    Reservation save(Reservation reservation);
    Optional<Reservation> findById(UUID id);
    Optional<Reservation> findByIdForUpdate(UUID id);
    List<Reservation> findExpiredForUpdate(Instant now, int limit);
}
