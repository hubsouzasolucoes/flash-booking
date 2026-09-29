package com.samuel.flashbooking.interfaces.rest.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class EventDtos {
    private EventDtos() {}
    public record CreateRequest(
            @Schema(example = "Java Conference") @NotBlank @Size(max = 160) String name,
            @Schema(example = "2027-10-01T18:00:00Z") @NotNull @Future Instant startsAt,
            @Schema(example = "100", minimum = "1") @Min(1) int capacity) {}
    public record Response(UUID id, String name, Instant startsAt, int capacity, int availableTickets,
                           Instant createdAt) {}
}
