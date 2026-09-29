package com.samuel.flashbooking.controller;

import com.samuel.flashbooking.dto.ApiDtos.*;
import com.samuel.flashbooking.service.ReservationService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Tag(name = "Reservations")
public class ReservationController {
    private final ReservationService service;

    public ReservationController(ReservationService s) {
        service = s;
    }

    @PostMapping("/events/{eventId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reservar ingressos", description = "Idempotente por Idempotency-Key. A capacidade é decrementada atomicamente no PostgreSQL para impedir oversell entre múltiplas instâncias.")
    public ReservationResponse create(@PathVariable UUID eventId, @Valid @RequestBody CreateReservationRequest r, @RequestHeader("Idempotency-Key") String key) {
        return service.create(eventId, r, key);
    }

    @GetMapping("/reservations/{id}")
    @Operation(summary = "Consultar reserva")
    public ReservationResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @DeleteMapping("/reservations/{id}")
    @Operation(summary = "Cancelar reserva")
    public ReservationResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
