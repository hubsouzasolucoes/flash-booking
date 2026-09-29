package com.samuel.flashbooking.dto;

import com.samuel.flashbooking.domain.ReservationStatus;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record CreateEventRequest(@NotBlank @Size(max = 160) String name, @NotNull @Future Instant startsAt,
                                     @Min(1) int capacity) {
    }

    public record EventResponse(UUID id, String name, Instant startsAt, int capacity, int availableTickets,
                                Instant createdAt) {
    }

    public record CreateReservationRequest(@Min(1) int quantity) {
    }

    public record ReservationResponse(UUID id, UUID eventId, int quantity, ReservationStatus status, Instant expiresAt,
                                      Instant createdAt, Instant updatedAt) {
    }
}
