package com.samuel.flashbooking.infrastructure.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import com.samuel.flashbooking.infrastructure.persistence.repository.JpaOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

@Configuration
public class ApplicationConfiguration {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    MeterBinder outboxPendingGauge(JpaOutboxRepository outbox) {
        return registry -> Gauge.builder("booking.outbox.pending", outbox,
                        repository -> repository.countByStatusIn(java.util.List.of("PENDING", "PROCESSING")))
                .description("Outbox events awaiting publication").register(registry);
    }
}
