package com.samuel.flashbooking.application.reservation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyStore {
    void lock(String key);
    Optional<Record> find(String key);
    void save(String key, String requestHash, UUID reservationId, Instant createdAt);

    record Record(String requestHash, UUID reservationId) {}
}
