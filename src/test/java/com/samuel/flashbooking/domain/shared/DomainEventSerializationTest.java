package com.samuel.flashbooking.domain.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventSerializationTest {
    @Test
    void roundTripsTheVersionedJsonEnvelopeWithoutJavaSerialization() throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        UUID eventId = UUID.randomUUID();
        var original = new DomainEvent(eventId, UUID.randomUUID(), "Event", 2,
                "ReservationCreated", 1, UUID.randomUUID(), null, Map.of("quantity", 2),
                Instant.parse("2026-09-29T12:00:00Z"));

        String json = mapper.writeValueAsString(original);
        DomainEvent restored = mapper.readValue(json, DomainEvent.class);

        assertThat(restored).isEqualTo(original);
        assertThat(json).contains("\"eventVersion\":1", "\"aggregateVersion\":2");
    }
}
