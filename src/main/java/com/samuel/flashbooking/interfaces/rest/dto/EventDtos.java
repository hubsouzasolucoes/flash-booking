package com.samuel.flashbooking.interfaces.rest.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class EventDtos {
    private EventDtos() {}
    public record CreateRequest(@NotBlank @Size(max = 160) String name, @NotNull @Future Instant startsAt,
                                @Min(1) int capacity) {}
    public record Response(UUID id, String name, Instant startsAt, int capacity, int availableTickets,
                           Instant createdAt) {}
}
