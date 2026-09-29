package com.samuel.flashbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlashBookingApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlashBookingApplication.class, args);
    }
}
