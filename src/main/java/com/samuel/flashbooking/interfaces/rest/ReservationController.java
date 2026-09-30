package com.samuel.flashbooking.interfaces.rest;

import com.samuel.flashbooking.application.reservation.ReservationUseCases;
import com.samuel.flashbooking.domain.reservation.Reservation;
import com.samuel.flashbooking.interfaces.rest.dto.ReservationDtos.CreateRequest;
import com.samuel.flashbooking.interfaces.rest.dto.ReservationDtos.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
@Tag(name = "Reservas", description = "Ciclo de vida das reservas")
public class ReservationController {
    private final ReservationUseCases reservations;

    public ReservationController(ReservationUseCases reservations) {
        this.reservations = reservations;
    }

    @PostMapping("/eventos/{eventId}/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar uma reserva", description = "Reserva ingressos atomicamente. Novas tentativas com a " +
            "mesma Idempotency-Key e o mesmo payload retornam a reserva original; um payload diferente retorna 409.", responses = {
            @ApiResponse(responseCode = "201", description = "Reserva criada ou recuperada em nova tentativa"),
            @ApiResponse(responseCode = "400", description = "Requisição ou Idempotency-Key inválida"),
            @ApiResponse(responseCode = "404", description = "Evento inexistente"),
            @ApiResponse(responseCode = "409", description = "Chave reutilizada com payload diferente"),
            @ApiResponse(responseCode = "422", description = "Capacidade insuficiente")})
    public Response create(@PathVariable UUID eventId, @Valid @RequestBody CreateRequest request,
                           @Parameter(description = "Chave única da operação (máximo de 160 caracteres). Nova tentativa com o mesmo evento e " +
                                   "quantidade retorna a reserva original; reutilização com payload diferente retorna 409.", required = true,
                                   example = "checkout-123-attempt-1")
                           @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 160) String key) {
        return response(reservations.create(eventId, request.quantity(), key));
    }

    @GetMapping("/reservas/{id}")
    @Operation(summary = "Consultar uma reserva", responses = {
            @ApiResponse(responseCode = "200", description = "Reserva encontrada"),
            @ApiResponse(responseCode = "404", description = "Reserva inexistente")})
    public Response get(@PathVariable UUID id) {
        return response(reservations.get(id));
    }

    @DeleteMapping("/reservas/{id}")
    @Operation(summary = "Cancelar uma reserva pendente", description = "A operação é idempotente para reservas " +
            "CANCELLED: novas tentativas retornam o estado terminal sem liberar capacidade duas vezes. PENDING passa " +
            "a CANCELLED; EXPIRED retorna 409.", responses = {
            @ApiResponse(responseCode = "200", description = "Reserva cancelada ou cancelamento anterior recuperado"),
            @ApiResponse(responseCode = "404", description = "Reserva inexistente"),
            @ApiResponse(responseCode = "409", description = "Reserva não está pendente")})
    public Response cancel(@PathVariable UUID id) {
        return response(reservations.cancel(id));
    }

    private Response response(Reservation reservation) {
        return new Response(reservation.id(), reservation.eventId(), reservation.quantity(), reservation.status(),
                reservation.expiresAt(), reservation.createdAt(), reservation.updatedAt());
    }
}
