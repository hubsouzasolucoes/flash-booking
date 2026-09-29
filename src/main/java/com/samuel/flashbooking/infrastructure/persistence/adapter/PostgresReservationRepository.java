package com.samuel.flashbooking.infrastructure.persistence.adapter;

import com.samuel.flashbooking.application.reservation.ReservationRepository;
import com.samuel.flashbooking.domain.reservation.Reservation;
import com.samuel.flashbooking.infrastructure.persistence.entity.ReservationEntity;
import com.samuel.flashbooking.infrastructure.persistence.repository.JpaReservationRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresReservationRepository implements ReservationRepository {
    private final JpaReservationRepository repository;

    public PostgresReservationRepository(JpaReservationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Reservation save(Reservation reservation) {
        return domain(repository.save(entity(reservation)));
    }

    @Override
    public Optional<Reservation> findById(UUID id) {
        return repository.findById(id).map(this::domain);
    }

    @Override
    public Optional<Reservation> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(this::domain);
    }

    @Override
    public List<Reservation> findExpiredForUpdate(Instant now, int limit) {
        return repository.findExpiredForUpdate(now, limit).stream().map(this::domain).toList();
    }

    private ReservationEntity entity(Reservation r) {
        return new ReservationEntity(r.id(), r.eventId(), r.quantity(), r.status(), r.expiresAt(), r.createdAt(), r.updatedAt());
    }

    private Reservation domain(ReservationEntity r) {
        return new Reservation(r.id, r.eventId, r.quantity, r.status, r.expiresAt, r.createdAt, r.updatedAt);
    }
}
