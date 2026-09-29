package com.samuel.flashbooking.infrastructure.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxClaims claims;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final String topic;
    private final Counter success;
    private final Counter failure;

    public OutboxPublisher(OutboxClaims claims, KafkaTemplate<String, String> kafka, Clock clock,
                           MeterRegistry metrics, @Value("${app.kafka.events-topic}") String topic) {
        this.claims = claims; this.kafka = kafka; this.clock = clock; this.topic = topic;
        this.success = metrics.counter("outbox.publish.success");
        this.failure = metrics.counter("outbox.publish.failure");
    }

    @Scheduled(fixedDelayString = "${app.outbox-fixed-delay}")
    public void publishBatch() {
        for (var event : claims.claim(clock.instant())) {
            try {
                kafka.send(topic, event.aggregateId.toString(), event.payload).get();
                claims.published(event.id, clock.instant());
                success.increment();
                log.info("outbox event published eventId={} aggregateId={} eventType={}", event.id, event.aggregateId, event.eventType);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                retry(event, exception);
                return;
            } catch (Exception exception) {
                retry(event, exception);
            }
        }
    }

    private void retry(com.samuel.flashbooking.infrastructure.persistence.entity.OutboxEventEntity event, Exception exception) {
        long seconds = Math.min(300, 5L * (1L << Math.min(event.attempts, 6)));
        claims.failed(event.id, clock.instant().plus(Duration.ofSeconds(seconds)), exception.getMessage());
        failure.increment();
        log.warn("outbox retry eventId={} aggregateId={} eventType={} attempt={} delaySeconds={}",
                event.id, event.aggregateId, event.eventType, event.attempts + 1, seconds);
    }
}
