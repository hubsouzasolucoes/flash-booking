package com.samuel.flashbooking.interfaces.rest.dto;

import com.samuel.flashbooking.domain.reservation.ReservationStatus;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class ReservationDtos {
    private ReservationDtos() {}
    public record CreateRequest(@Schema(example = "2", minimum = "1") @Min(1) int quantity) {}
    public record Response(UUID id, UUID eventId, int quantity, ReservationStatus status, Instant expiresAt,
                           Instant createdAt, Instant updatedAt) {}
}
