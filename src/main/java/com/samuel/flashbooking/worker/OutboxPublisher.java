package com.samuel.flashbooking.worker;

import com.samuel.flashbooking.repository.OutboxRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class OutboxPublisher {
    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(OutboxRepository o, KafkaTemplate<String, String> k) {
        outbox = o;
        kafka = k;
    }

    @Scheduled(fixedDelayString = "${app.outbox-fixed-delay}")
    @Transactional
    public void publish() {
        for (var event : outbox.unpublished()) {
            try {
                kafka.send("flash-booking.events", event.getAggregateId().toString(), event.getPayload()).get();
                event.published(Instant.now());
            } catch (Exception e) {
                break;
            }
        }
    }
}
