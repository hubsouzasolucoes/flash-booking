package com.samuel.flashbooking.infrastructure.messaging;

import com.samuel.flashbooking.infrastructure.persistence.entity.OutboxEventEntity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private final OutboxClaims claims = mock(OutboxClaims.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxPublisher publisher = new OutboxPublisher(claims, kafka,
            Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry(), "events");

    @Test
    void movesPendingEventToPublishedAfterBrokerAcknowledgement() {
        var event = event();
        when(claims.claim(NOW)).thenReturn(List.of(event));
        when(kafka.send("events", event.aggregateId.toString(), event.payload)).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishBatch();

        verify(claims).published(event.id, NOW);
        verify(claims, never()).failed(any(), any(), any());
    }

    @Test
    void schedulesBackoffWhenKafkaFails() {
        var event = event();
        when(claims.claim(NOW)).thenReturn(List.of(event));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("down")));

        publisher.publishBatch();

        verify(claims).failed(eq(event.id), eq(NOW.plusSeconds(5)), anyString());
        verify(claims, never()).published(any(), any());
    }

    private OutboxEventEntity event() {
        return new OutboxEventEntity(UUID.randomUUID(), UUID.randomUUID(), "Event", 1, "EventCreated", 1, "{}", NOW);
    }
}
