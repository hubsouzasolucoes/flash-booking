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
@RequestMapping("/eventos")
@Tag(name = "Eventos", description = "Cadastro de eventos e consulta de capacidade")
public class EventController {
    private final EventUseCases events;

    public EventController(EventUseCases events) {
        this.events = events;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar um evento", description = "Cria o inventário autoritativo de um evento futuro.", responses = {
            @ApiResponse(responseCode = "201", description = "Evento criado"),
            @ApiResponse(responseCode = "400", description = "Corpo inválido")})
    public Response create(@Valid @RequestBody CreateRequest request) {
        return response(events.create(request.name(), request.startsAt(), request.capacity()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar a disponibilidade de um evento", description = "Retorna o modelo de leitura de " +
            "disponibilidade. O valor pode ficar brevemente desatualizado enquanto a projeção assíncrona via " +
            "Outbox/Kafka estiver sendo processada.", responses = {
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
