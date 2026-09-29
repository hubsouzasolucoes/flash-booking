package com.samuel.flashbooking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.flashbooking.domain.Event;
import com.samuel.flashbooking.domain.OutboxEvent;
import com.samuel.flashbooking.dto.ApiDtos.CreateEventRequest;
import com.samuel.flashbooking.dto.ApiDtos.EventResponse;
import com.samuel.flashbooking.exception.BusinessException;
import com.samuel.flashbooking.repository.EventRepository;
import com.samuel.flashbooking.repository.OutboxRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class EventService {
    private final EventRepository events;
    private final OutboxRepository outbox;
    private final ObjectMapper json;

    public EventService(EventRepository e, OutboxRepository o, ObjectMapper j) {
        events = e;
        outbox = o;
        json = j;
    }

    @Transactional
    public EventResponse create(CreateEventRequest r) {
        var now = Instant.now();
        var e = events.save(new Event(UUID.randomUUID(), r.name(), r.startsAt(), r.capacity(), now));
        try {
            outbox.save(new OutboxEvent(e.getId(), "Event", "EventCreated", json.writeValueAsString(Map.of("eventId", e.getId(), "capacity", e.getCapacity())), now));
        } catch (Exception x) {
            throw new IllegalStateException(x);
        }
        return map(e);
    }

    @Transactional(readOnly = true)
    public EventResponse get(UUID id) {
        return map(events.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found")));
    }

    private EventResponse map(Event e) {
        return new EventResponse(e.getId(), e.getName(), e.getStartsAt(), e.getCapacity(), e.getAvailableTickets(), e.getCreatedAt());
    }
}
