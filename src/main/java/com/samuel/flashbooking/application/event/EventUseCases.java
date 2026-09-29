package com.samuel.flashbooking.application.event;

import com.samuel.flashbooking.application.ApplicationException;
import com.samuel.flashbooking.application.DomainEventOutbox;
import com.samuel.flashbooking.application.CorrelationIds;
import com.samuel.flashbooking.domain.event.Event;
import com.samuel.flashbooking.domain.shared.DomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.samuel.flashbooking.application.ApplicationException.ErrorCode.EVENT_NOT_FOUND;

@Service
public class EventUseCases {
    private final EventRepository events;
    private final DomainEventOutbox outbox;
    private final Clock clock;
    private final EventAvailability availability;

    public EventUseCases(EventRepository events, DomainEventOutbox outbox, Clock clock, EventAvailability availability) {
        this.events = events;
        this.outbox = outbox;
        this.clock = clock;
        this.availability = availability;
    }

    @Transactional
    public Event create(String name, Instant startsAt, int capacity) {
        Instant now = clock.instant();
        Event event = events.save(Event.create(name, startsAt, capacity, now));
        UUID eventId = UUID.randomUUID();
        outbox.append(new DomainEvent(eventId, event.id(), "Event", event.version(), "EventCreated", 1,
                CorrelationIds.currentOrNew(), null, Map.of("eventId", event.id(), "name", event.name(), "startsAt", event.startsAt(),
                "capacity", event.capacity(), "availableTickets", event.availableTickets(),
                "createdAt", event.createdAt()), now));
        return event;
    }

    @Transactional(readOnly = true)
    public EventAvailability.View getAvailability(UUID id) {
        return availability.findById(id)
                .orElseThrow(() -> new ApplicationException(EVENT_NOT_FOUND, "Event projection not found yet"));
    }
}
