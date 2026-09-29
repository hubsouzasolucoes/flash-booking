package com.samuel.flashbooking.interfaces.rest;

import com.samuel.flashbooking.application.event.EventUseCases;
import com.samuel.flashbooking.domain.event.Event;
import com.samuel.flashbooking.interfaces.rest.dto.EventDtos.CreateRequest;
import com.samuel.flashbooking.interfaces.rest.dto.EventDtos.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/events")
@Tag(name = "Events", description = "Cadastro de eventos e consulta de capacidade")
public class EventController {
    private final EventUseCases events;

    public EventController(EventUseCases events) {
        this.events = events;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an event", description = "Creates the authoritative inventory for a future event.", responses = {
            @ApiResponse(responseCode = "201", description = "Evento criado"),
            @ApiResponse(responseCode = "400", description = "Corpo inválido")})
    public Response create(@Valid @RequestBody CreateRequest request) {
        return response(events.create(request.name(), request.startsAt(), request.capacity()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event availability", description = "Returns the availability read model. The value may " +
            "be briefly stale while the asynchronous Outbox/Kafka projection is processing.", responses = {
            @ApiResponse(responseCode = "200", description = "Evento encontrado"),
            @ApiResponse(responseCode = "404", description = "Evento inexistente")})
    public Response get(@PathVariable UUID id) {
        var event = events.getAvailability(id);
        return new Response(event.id(), event.name(), event.startsAt(), event.capacity(),
                event.availableTickets(), event.createdAt());
    }

    private Response response(Event event) {
        return new Response(event.id(), event.name(), event.startsAt(), event.capacity(),
                event.availableTickets(), event.createdAt());
    }
}
