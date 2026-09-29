package com.samuel.flashbooking.application.event;

import com.samuel.flashbooking.application.ApplicationException;
import com.samuel.flashbooking.application.DomainEventOutbox;
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

    public EventUseCases(EventRepository events, DomainEventOutbox outbox, Clock clock) {
        this.events = events;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public Event create(String name, Instant startsAt, int capacity) {
        Instant now = clock.instant();
        Event event = events.save(Event.create(name, startsAt, capacity, now));
        outbox.append(new DomainEvent(event.id(), "Event", "EventCreated",
                Map.of("eventId", event.id(), "capacity", event.capacity()), now));
        return event;
    }

    @Transactional(readOnly = true)
    public Event get(UUID id) {
        return events.findById(id)
                .orElseThrow(() -> new ApplicationException(EVENT_NOT_FOUND, "Event not found"));
    }
}
