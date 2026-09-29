package com.samuel.flashbooking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.flashbooking.domain.IdempotencyRecord;
import com.samuel.flashbooking.domain.OutboxEvent;
import com.samuel.flashbooking.domain.Reservation;
import com.samuel.flashbooking.domain.ReservationStatus;
import com.samuel.flashbooking.dto.ApiDtos.CreateReservationRequest;
import com.samuel.flashbooking.dto.ApiDtos.ReservationResponse;
import com.samuel.flashbooking.exception.BusinessException;
import com.samuel.flashbooking.repository.EventRepository;
import com.samuel.flashbooking.repository.IdempotencyRepository;
import com.samuel.flashbooking.repository.OutboxRepository;
import com.samuel.flashbooking.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class ReservationService {
    private final EventRepository events;
    private final ReservationRepository reservations;
    private final IdempotencyRepository idem;
    private final OutboxRepository outbox;
    private final ObjectMapper json;
    private final Duration ttl;

    public ReservationService(EventRepository e, ReservationRepository r, IdempotencyRepository i, OutboxRepository o, ObjectMapper j, @Value("${app.reservation-ttl}") Duration ttl) {
        events = e;
        reservations = r;
        idem = i;
        outbox = o;
        json = j;
        this.ttl = ttl;
    }

    @Transactional
    public ReservationResponse create(UUID eventId, CreateReservationRequest req, String key) {
        if (key == null || key.isBlank())
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
        String hash = hash(eventId + ":" + req.quantity());
        var previous = idem.findByKey(key);
        if (previous.isPresent()) {
            if (!previous.get().getRequestHash().equals(hash))
                throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key was already used with a different request");
            return get(previous.get().getReservationId());
        }
        if (!events.existsById(eventId))
            throw new BusinessException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found");
        if (events.reserve(eventId, req.quantity()) == 0)
            throw new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_CAPACITY", "Not enough tickets available");
        var now = Instant.now();
        var reservation = reservations.save(new Reservation(UUID.randomUUID(), eventId, req.quantity(), now.plus(ttl), now));
        try {
            idem.save(new IdempotencyRecord(UUID.randomUUID(), key, hash, reservation.getId(), now));
            outbox.save(new OutboxEvent(reservation.getId(), "Reservation", "ReservationCreated", json.writeValueAsString(Map.of("reservationId", reservation.getId(), "eventId", eventId, "quantity", req.quantity())), now));
        } catch (DataIntegrityViolationException x) {
            throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Concurrent request used the same idempotency key");
        } catch (Exception x) {
            throw new IllegalStateException(x);
        }
        return map(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(UUID id) {
        return map(reservations.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Reservation not found")));
    }

    @Transactional
    public ReservationResponse cancel(UUID id) {
        var r = reservations.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Reservation not found"));
        if (r.getStatus() != ReservationStatus.PENDING)
            throw new BusinessException(HttpStatus.CONFLICT, "RESERVATION_NOT_CANCELLABLE", "Only pending reservations can be cancelled");
        var now = Instant.now();
        r.cancel(now);
        events.release(r.getEventId(), r.getQuantity());
        outbox.save(new OutboxEvent(r.getId(), "Reservation", "ReservationCancelled", payload(r), now));
        return map(r);
    }

    @Transactional
    public void expireBatch() {
        var now = Instant.now();
        for (var r : reservations.findExpiredForUpdate(now)) {
            r.expire(now);
            events.release(r.getEventId(), r.getQuantity());
            outbox.save(new OutboxEvent(r.getId(), "Reservation", "ReservationExpired", payload(r), now));
        }
    }

    private String payload(Reservation r) {
        try {
            return json.writeValueAsString(Map.of("reservationId", r.getId(), "eventId", r.getEventId(), "quantity", r.getQuantity(), "status", r.getStatus()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ReservationResponse map(Reservation r) {
        return new ReservationResponse(r.getId(), r.getEventId(), r.getQuantity(), r.getStatus(), r.getExpiresAt(), r.getCreatedAt(), r.getUpdatedAt());
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
