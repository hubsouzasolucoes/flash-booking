package com.samuel.flashbooking.infrastructure.scheduling;

import com.samuel.flashbooking.application.reservation.ReservationUseCases;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpirationScheduler {
    private final ReservationUseCases reservations;
    public ReservationExpirationScheduler(ReservationUseCases reservations) { this.reservations = reservations; }

    @Scheduled(fixedDelayString = "${app.expiration-fixed-delay}")
    public void expireReservations() { reservations.expireBatch(); }
}
