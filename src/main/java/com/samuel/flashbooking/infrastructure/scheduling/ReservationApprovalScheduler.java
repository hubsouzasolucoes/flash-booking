package com.samuel.flashbooking.infrastructure.scheduling;

import com.samuel.flashbooking.application.reservation.ReservationUseCases;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ReservationApprovalScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReservationApprovalScheduler.class);
    private final ReservationUseCases reservations;

    public ReservationApprovalScheduler(ReservationUseCases reservations) {
        this.reservations = reservations;
    }

    @Scheduled(fixedDelayString = "${app.approval-fixed-delay}")
    public void approveReservations() {
        int approved = reservations.approveBatch();
        if (approved > 0) log.info("event=approval_batch_completed approvedCount={}", approved);
    }
}
