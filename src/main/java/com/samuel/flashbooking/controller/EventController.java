package com.samuel.flashbooking.controller;

import com.samuel.flashbooking.dto.ApiDtos.CreateEventRequest;
import com.samuel.flashbooking.dto.ApiDtos.EventResponse;
import com.samuel.flashbooking.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/events")
@Tag(name = "Events")
public class EventController {
    private final EventService service;

    public EventController(EventService s) {
        service = s;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar evento")
    public EventResponse create(@Valid @RequestBody CreateEventRequest r) {
        return service.create(r);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar disponibilidade do evento")
    public EventResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
