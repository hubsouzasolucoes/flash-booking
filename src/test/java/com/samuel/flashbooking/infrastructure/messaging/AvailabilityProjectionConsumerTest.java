package com.samuel.flashbooking.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityProjectionConsumerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AvailabilityProjectionConsumer consumer = new AvailabilityProjectionConsumer(
            new ObjectMapper().registerModule(new JavaTimeModule()), jdbc,
            Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC), new SimpleMeterRegistry());

    @Test
    void appliesReservationOnceAndInboxIgnoresRedelivery() {
        when(jdbc.update(startsWith("INSERT INTO inbox_events"), any(Object[].class))).thenReturn(1, 0);
        when(jdbc.update(startsWith("UPDATE event_availability_projection"), any(Object[].class))).thenReturn(1);
        String event = reservationEvent(2);

        consumer.consume(event);
        consumer.consume(event);

        verify(jdbc, times(1)).update(startsWith("UPDATE event_availability_projection"), any(Object[].class));
        assertThat(org.slf4j.MDC.get("correlationId")).isNull();
    }

    @Test
    void ignoresAnOlderAggregateVersionWithoutRegressingProjection() {
        when(jdbc.update(startsWith("INSERT INTO inbox_events"), any(Object[].class))).thenReturn(1);
        when(jdbc.update(startsWith("UPDATE event_availability_projection"), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(startsWith("SELECT last_event_version"), eq(Long.class), any(UUID.class))).thenReturn(5L);

        consumer.consume(reservationEvent(4));

        verify(jdbc, never()).update(startsWith("UPDATE event_availability_projection SET available_tickets=99"));
    }

    private String reservationEvent(long version) {
        return """
                {"eventId":"%s","eventType":"ReservationCreated","eventVersion":1,
                 "aggregateId":"%s","aggregateType":"Event","aggregateVersion":%d,
                 "occurredAt":"2026-09-29T12:00:00Z","correlationId":"%s",
                 "payload":{"availableTickets":8,"quantity":2}}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), version, UUID.randomUUID());
    }
}
