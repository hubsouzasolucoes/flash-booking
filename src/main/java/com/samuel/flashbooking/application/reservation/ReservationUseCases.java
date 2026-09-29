package com.samuel.flashbooking.application.reservation;

import com.samuel.flashbooking.application.ApplicationException;
import com.samuel.flashbooking.application.DomainEventOutbox;
import com.samuel.flashbooking.application.event.EventRepository;
import com.samuel.flashbooking.domain.reservation.Reservation;
import com.samuel.flashbooking.domain.shared.DomainEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static com.samuel.flashbooking.application.ApplicationException.ErrorCode.*;

@Service
public class ReservationUseCases {
    private static final int EXPIRATION_BATCH_SIZE = 100;
    private final EventRepository events;
    private final ReservationRepository reservations;
    private final IdempotencyStore idempotency;
    private final DomainEventOutbox outbox;
    private final Clock clock;
    private final Duration ttl;

    public ReservationUseCases(EventRepository events, ReservationRepository reservations,
            IdempotencyStore idempotency, DomainEventOutbox outbox, Clock clock,
            @Value("${app.reservation-ttl}") Duration ttl) {
        this.events = events;
        this.reservations = reservations;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Transactional
    public Reservation create(UUID eventId, int quantity, String key) {
        String requestHash = hash(eventId + ":" + quantity);
        // A PostgreSQL transaction-scoped advisory lock serializes one key across every API instance.
        idempotency.lock(key);
        var previous = idempotency.find(key);
        if (previous.isPresent()) {
            if (!previous.get().requestHash().equals(requestHash)) {
                throw new ApplicationException(IDEMPOTENCY_CONFLICT,
                        "Idempotency key was already used with a different request");
            }
            return getRequired(previous.get().reservationId());
        }
        if (!events.existsById(eventId)) {
            throw new ApplicationException(EVENT_NOT_FOUND, "Event not found");
        }
        if (!events.reserveCapacity(eventId, quantity)) {
            throw new ApplicationException(INSUFFICIENT_CAPACITY, "Not enough tickets available");
        }

        Instant now = clock.instant();
        Reservation reservation = reservations.save(Reservation.create(eventId, quantity, now.plus(ttl), now));
        idempotency.save(key, requestHash, reservation.id(), now);
        outbox.append(event("ReservationCreated", reservation, now));
        return reservation;
    }

    @Transactional(readOnly = true)
    public Reservation get(UUID id) {
        return getRequired(id);
    }

    @Transactional
    public Reservation cancel(UUID id) {
        Reservation reservation = reservations.findByIdForUpdate(id)
                .orElseThrow(() -> new ApplicationException(RESERVATION_NOT_FOUND, "Reservation not found"));
        Instant now = clock.instant();
        if (!reservation.cancel(now)) {
            throw new ApplicationException(RESERVATION_NOT_CANCELLABLE,
                    "Only pending reservations can be cancelled");
        }
        reservations.save(reservation);
        events.releaseCapacity(reservation.eventId(), reservation.quantity());
        outbox.append(event("ReservationCancelled", reservation, now));
        return reservation;
    }

    @Transactional
    public int expireBatch() {
        Instant now = clock.instant();
        int expired = 0;
        for (Reservation reservation : reservations.findExpiredForUpdate(now, EXPIRATION_BATCH_SIZE)) {
            if (reservation.expire(now)) {
                reservations.save(reservation);
                events.releaseCapacity(reservation.eventId(), reservation.quantity());
                outbox.append(event("ReservationExpired", reservation, now));
                expired++;
            }
        }
        return expired;
    }

    private Reservation getRequired(UUID id) {
        return reservations.findById(id)
                .orElseThrow(() -> new ApplicationException(RESERVATION_NOT_FOUND, "Reservation not found"));
    }

    private DomainEvent event(String type, Reservation reservation, Instant now) {
        return new DomainEvent(reservation.id(), "Reservation", type,
                Map.of("reservationId", reservation.id(), "eventId", reservation.eventId(),
                        "quantity", reservation.quantity(), "status", reservation.status()), now);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }
}
