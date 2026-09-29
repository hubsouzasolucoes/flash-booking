package com.samuel.flashbooking.worker;

import com.samuel.flashbooking.service.ReservationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpirationWorker {
    private final ReservationService service;

    public ExpirationWorker(ReservationService s) {
        service = s;
    }

    @Scheduled(fixedDelayString = "${app.expiration-fixed-delay}")
    public void run() {
        service.expireBatch();
    }
}
