package com.samuel.flashbooking.infrastructure.messaging;

import com.samuel.flashbooking.infrastructure.persistence.repository.JpaOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final JpaOutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final String topic;

    public OutboxPublisher(JpaOutboxRepository outbox, KafkaTemplate<String, String> kafka, Clock clock,
                           @Value("${app.kafka.events-topic}") String topic) {
        this.outbox = outbox; this.kafka = kafka; this.clock = clock; this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${app.outbox-fixed-delay}")
    @Transactional
    public void publishBatch() {
        for (var event : outbox.findUnpublishedForUpdate()) {
            try {
                kafka.send(topic, event.aggregateId.toString(), event.payload).get();
                event.markPublished(clock.instant());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("Outbox publication interrupted; event remains pending");
                return;
            } catch (Exception exception) {
                log.warn("Outbox publication failed; event remains pending: {}", exception.getMessage());
                return;
            }
        }
    }
}
