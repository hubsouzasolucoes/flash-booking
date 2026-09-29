package com.samuel.flashbooking.interfaces.rest.dto;

import com.samuel.flashbooking.domain.reservation.ReservationStatus;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;

public final class ReservationDtos {
    private ReservationDtos() {}
    public record CreateRequest(@Min(1) int quantity) {}
    public record Response(UUID id, UUID eventId, int quantity, ReservationStatus status, Instant expiresAt,
                           Instant createdAt, Instant updatedAt) {}
}
