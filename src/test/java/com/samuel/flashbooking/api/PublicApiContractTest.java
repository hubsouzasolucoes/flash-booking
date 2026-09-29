package com.samuel.flashbooking.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.flashbooking.application.ApplicationException;
import com.samuel.flashbooking.application.event.EventAvailability;
import com.samuel.flashbooking.application.event.EventUseCases;
import com.samuel.flashbooking.application.reservation.ReservationUseCases;
import com.samuel.flashbooking.domain.event.Event;
import com.samuel.flashbooking.domain.reservation.Reservation;
import com.samuel.flashbooking.interfaces.rest.EventController;
import com.samuel.flashbooking.interfaces.rest.ReservationController;
import com.samuel.flashbooking.interfaces.rest.error.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.UUID;

import static com.samuel.flashbooking.application.ApplicationException.ErrorCode.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicApiContractTest {
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private final EventUseCases events = mock(EventUseCases.class);
    private final ReservationUseCases reservations = mock(ReservationUseCases.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new EventController(events), new ReservationController(reservations))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createsEventAndPreservesPublicResponseFields() throws Exception {
        Event event = Event.create("Java Conference", NOW.plusSeconds(86_400), 10, NOW);
        when(events.create(anyString(), any(), eq(10))).thenReturn(event);

        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java Conference\",\"startsAt\":\"2100-09-30T12:00:00Z\",\"capacity\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(event.id().toString()))
                .andExpect(jsonPath("$.capacity").value(10))
                .andExpect(jsonPath("$.availableTickets").value(10));
    }

    @Test
    void rejectsInvalidAndMalformedEventBodiesWithProblemDetail() throws Exception {
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"startsAt\":null,\"capacity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:flash-booking:problem:invalid-request"))
                .andExpect(jsonPath("$.title").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"));
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request is malformed or incomplete"));
    }

    @Test
    void getsEventAndMapsMissingProjectionToNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(events.getAvailability(id)).thenReturn(new EventAvailability.View(id, "Talk", NOW.plusSeconds(3600),
                20, 17, NOW, NOW, 4));
        mvc.perform(get("/events/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTickets").value(17));

        UUID missing = UUID.randomUUID();
        when(events.getAvailability(missing)).thenThrow(new ApplicationException(EVENT_NOT_FOUND, "not found"));
        mvc.perform(get("/events/{id}", missing)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("EVENT_NOT_FOUND"));
    }

    @Test
    void reservationEndpointsPreserveContractAndRequireIdempotencyKey() throws Exception {
        UUID eventId = UUID.randomUUID();
        Reservation reservation = Reservation.create(eventId, 2, NOW.plusSeconds(600), NOW);
        when(reservations.create(eventId, 2, "checkout-1")).thenReturn(reservation);
        when(reservations.get(reservation.id())).thenReturn(reservation);
        reservation.cancel(NOW.plusSeconds(1));
        when(reservations.cancel(reservation.id())).thenReturn(reservation);

        mvc.perform(post("/events/{id}/reservations", eventId).header("Idempotency-Key", "checkout-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.quantity").value(2));
        mvc.perform(get("/reservations/{id}", reservation.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(reservation.id().toString()));
        mvc.perform(delete("/reservations/{id}", reservation.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/events/{id}/reservations", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservationFailuresUseStableHttpSemanticsWithoutLeakingInternals() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(reservations.create(eq(eventId), anyInt(), anyString()))
                .thenThrow(new ApplicationException(INSUFFICIENT_CAPACITY, "Not enough tickets available"));
        mvc.perform(post("/events/{id}/reservations", eventId).header("Idempotency-Key", "sellout")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("INSUFFICIENT_CAPACITY"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQLException"))));

        UUID missing = UUID.randomUUID();
        when(reservations.get(missing)).thenThrow(new ApplicationException(RESERVATION_NOT_FOUND, "not found"));
        mvc.perform(get("/reservations/{id}", missing)).andExpect(status().isNotFound());
    }
}
