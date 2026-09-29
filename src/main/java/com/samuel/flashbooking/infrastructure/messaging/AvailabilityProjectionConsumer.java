package com.samuel.flashbooking.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class AvailabilityProjectionConsumer {
    private static final Logger log = LoggerFactory.getLogger(AvailabilityProjectionConsumer.class);
    private static final String CONSUMER = "availability-projection-v1";
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Counter processed;
    private final Counter duplicates;
    private final Counter failures;

    public AvailabilityProjectionConsumer(ObjectMapper objectMapper, JdbcTemplate jdbc, Clock clock, MeterRegistry metrics) {
        this.objectMapper = objectMapper; this.jdbc = jdbc; this.clock = clock;
        processed = metrics.counter("booking.consumer.processed", "consumer", CONSUMER);
        duplicates = metrics.counter("booking.consumer.duplicate", "consumer", CONSUMER);
        failures = metrics.counter("booking.consumer.failed", "consumer", CONSUMER);
    }

    @KafkaListener(topics = "${app.kafka.events-topic}", groupId = "${app.kafka.consumer-group}")
    @Transactional
    public void consume(String json) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(json);
            validate(envelope);
        } catch (Exception exception) {
            failures.increment();
            log.error("consumer failure: invalid event envelope", exception);
            throw new InvalidEventException("Invalid event envelope", exception);
        }
        UUID eventId = UUID.fromString(envelope.path("eventId").asText());
        UUID aggregateId = UUID.fromString(envelope.path("aggregateId").asText());
        String eventType = envelope.path("eventType").asText();
        UUID correlationId = UUID.fromString(envelope.path("correlationId").asText());
        MDC.put("correlationId", correlationId.toString());
        try {
            process(envelope, eventId, aggregateId, eventType, correlationId);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void process(JsonNode envelope, UUID eventId, UUID aggregateId, String eventType, UUID correlationId) {
        int inserted = jdbc.update("INSERT INTO inbox_events(event_id,consumer,processed_at) VALUES (?,?,?) " +
                "ON CONFLICT (event_id,consumer) DO NOTHING", eventId, CONSUMER, clock.instant());
        if (inserted == 0) {
            duplicates.increment();
            log.info("event=consumer_duplicate eventId={} aggregateId={} eventType={} correlationId={}",
                    eventId, aggregateId, eventType, correlationId);
            return;
        }
        try {
            apply(envelope, aggregateId, eventType);
            processed.increment();
            log.debug("event=projection_updated eventId={} aggregateId={} eventType={} correlationId={}",
                    eventId, aggregateId, eventType, correlationId);
        } catch (RuntimeException exception) {
            failures.increment();
            throw exception;
        }
    }

    private void apply(JsonNode event, UUID aggregateId, String type) {
        long version = event.path("aggregateVersion").asLong();
        JsonNode payload = event.path("payload");
        Instant occurredAt = Instant.parse(event.path("occurredAt").asText());
        if ("EventCreated".equals(type)) {
            jdbc.update("INSERT INTO event_availability_projection(event_id,name,starts_at,total_capacity,available_tickets," +
                            "created_at,updated_at,last_event_version) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (event_id) DO NOTHING",
                    aggregateId, text(payload, "name"), Instant.parse(text(payload, "startsAt")), payload.path("capacity").asInt(),
                    payload.path("availableTickets").asInt(), Instant.parse(text(payload, "createdAt")), occurredAt, version);
            return;
        }
        if (!java.util.Set.of("ReservationCreated", "ReservationCancelled", "ReservationExpired").contains(type)) return;
        int changed = jdbc.update("UPDATE event_availability_projection SET available_tickets=?,updated_at=?,last_event_version=? " +
                        "WHERE event_id=? AND last_event_version=?", payload.path("availableTickets").asInt(), occurredAt,
                version, aggregateId, version - 1);
        if (changed == 0) {
            Long current = jdbc.queryForObject("SELECT last_event_version FROM event_availability_projection WHERE event_id=?",
                    Long.class, aggregateId);
            if (current != null && current >= version) return;
            throw new EventOrderingGapException("Expected aggregate version " + (version - 1) + " before " + version);
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw new InvalidEventException("Missing payload field " + field);
        return value;
    }

    private static void validate(JsonNode event) {
        for (String field : java.util.List.of("eventId", "eventType", "eventVersion", "aggregateId", "aggregateType",
                "aggregateVersion", "occurredAt", "correlationId", "payload")) {
            if (event.path(field).isMissingNode() || event.path(field).isNull()) throw new InvalidEventException("Missing " + field);
        }
        if (event.path("eventVersion").asInt() != 1) throw new InvalidEventException("Unsupported event version");
    }

    public static class InvalidEventException extends RuntimeException {
        public InvalidEventException(String message) { super(message); }
        public InvalidEventException(String message, Throwable cause) { super(message, cause); }
    }
    public static class EventOrderingGapException extends RuntimeException {
        public EventOrderingGapException(String message) { super(message); }
    }
}
