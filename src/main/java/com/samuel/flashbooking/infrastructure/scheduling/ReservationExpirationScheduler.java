package com.samuel.flashbooking.infrastructure.scheduling;

import com.samuel.flashbooking.application.reservation.ReservationUseCases;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ReservationExpirationScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationScheduler.class);
    private final ReservationUseCases reservations;
    public ReservationExpirationScheduler(ReservationUseCases reservations) { this.reservations = reservations; }

    @Scheduled(fixedDelayString = "${app.expiration-fixed-delay}")
    public void expireReservations() {
        int expired = reservations.expireBatch();
        if (expired > 0) log.info("event=expiration_batch_completed expiredCount={}", expired);
    }
}
