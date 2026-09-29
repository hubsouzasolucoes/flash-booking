package com.samuel.flashbooking.concurrency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.flashbooking.application.ApplicationException;
import com.samuel.flashbooking.application.event.EventAvailability;
import com.samuel.flashbooking.application.event.EventUseCases;
import com.samuel.flashbooking.application.reservation.ReservationUseCases;
import com.samuel.flashbooking.domain.event.Event;
import com.samuel.flashbooking.domain.reservation.Reservation;
import com.samuel.flashbooking.infrastructure.outbox.JpaDomainEventOutbox;
import com.samuel.flashbooking.infrastructure.messaging.AvailabilityProjectionConsumer;
import com.samuel.flashbooking.infrastructure.persistence.adapter.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.samuel.flashbooking.application.ApplicationException.ErrorCode.INSUFFICIENT_CAPACITY;
import static com.samuel.flashbooking.application.ApplicationException.ErrorCode.RESERVATION_NOT_CANCELLABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext
@Import({EventUseCases.class, ReservationUseCases.class, PostgresEventRepository.class,
        PostgresReservationRepository.class, PostgresIdempotencyStore.class, JpaDomainEventOutbox.class,
        AvailabilityProjectionConsumer.class,
        ReservationConcurrencyIntegrationTest.Dependencies.class})
class ReservationConcurrencyIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.hikari.maximum-pool-size", () -> 30);
        properties.add("app.reservation-ttl", () -> "PT10M");
    }

    @Autowired EventUseCases events;
    @Autowired ReservationUseCases reservations;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired AvailabilityProjectionConsumer projection;

    @AfterEach
    void clean() {
        jdbc.update("TRUNCATE inbox_events,event_availability_projection,idempotency_records,outbox_events,reservations,events CASCADE");
    }

    @Test
    void oneHundredConcurrentRequestsNeverOversellTenTickets() throws Exception {
        for (int round = 0; round < 3; round++) {
            int currentRound = round;
            Event event = createEvent(10);
            AtomicInteger accepted = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();
            runConcurrently(100, attempt -> () -> {
                try {
                    reservations.create(event.id(), 1, "round-" + currentRound + "-request-" + attempt);
                    accepted.incrementAndGet();
                } catch (ApplicationException exception) {
                    assertThat(exception.code()).isEqualTo(INSUFFICIENT_CAPACITY);
                    rejected.incrementAndGet();
                }
                return null;
            });

            assertThat(accepted).hasValue(10);
            assertThat(rejected).hasValue(90);
            assertAccounting(event.id(), 10, 0, 10);
            jdbc.update("TRUNCATE idempotency_records,outbox_events,reservations,events CASCADE");
        }
    }

    @Test
    void variableConcurrentQuantitiesPreserveCapacityAccounting() throws Exception {
        Event event = createEvent(100);
        int[] quantities = IntStream.range(0, 80).map(i -> new int[]{1, 2, 3, 5}[i % 4]).toArray();
        runConcurrently(quantities.length, attempt -> () -> {
            try {
                reservations.create(event.id(), quantities[attempt], "variable-" + attempt);
            } catch (ApplicationException exception) {
                assertThat(exception.code()).isEqualTo(INSUFFICIENT_CAPACITY);
            }
            return null;
        });

        int available = available(event.id());
        int active = activeQuantity(event.id());
        assertThat(available).isBetween(0, 100);
        assertThat(active).isLessThanOrEqualTo(100);
        assertThat(available + active).isEqualTo(100);
    }

    @Test
    void concurrentRetriesCreateOneLogicalReservationAndDifferentPayloadConflicts() throws Exception {
        Event event = createEvent(10);
        List<Reservation> results = runConcurrently(50,
                ignored -> () -> reservations.create(event.id(), 1, "same-operation"));

        assertThat(results).extracting(Reservation::id).containsOnly(results.getFirst().id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE event_id=?", Long.class, event.id())).isOne();
        assertAccounting(event.id(), 10, 9, 1);

        assertThatThrownBy(() -> reservations.create(event.id(), 2, "same-operation"))
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApplicationException.ErrorCode.IDEMPOTENCY_CONFLICT));
        assertAccounting(event.id(), 10, 9, 1);
    }

    @Test
    void concurrentCancellationReturnsCapacityAndEmitsEventExactlyOnce() throws Exception {
        Event event = createEvent(10);
        Reservation reservation = reservations.create(event.id(), 5, "cancel-me");
        runConcurrently(20, ignored -> () -> reservations.cancel(reservation.id()));

        assertAccounting(event.id(), 10, 10, 0);
        assertThat(status(reservation.id())).isEqualTo("CANCELLED");
        assertThat(eventCount("ReservationCancelled", event.id())).isOne();
    }

    @Test
    void competingExpirationWorkersReleaseOnce() throws Exception {
        Event event = createEvent(10);
        Reservation reservation = reservations.create(event.id(), 5, "expire-me");
        jdbc.update("UPDATE reservations SET expires_at=? WHERE id=?", NOW.minusSeconds(1), reservation.id());

        runConcurrently(10, ignored -> () -> reservations.expireBatch());

        assertAccounting(event.id(), 10, 10, 0);
        assertThat(status(reservation.id())).isEqualTo("EXPIRED");
        assertThat(eventCount("ReservationExpired", event.id())).isOne();
    }

    @Test
    void cancellationAndExpirationRaceHasOneWinnerAndOneRelease() throws Exception {
        Event event = createEvent(10);
        Reservation reservation = reservations.create(event.id(), 5, "race-me");
        jdbc.update("UPDATE reservations SET expires_at=? WHERE id=?", NOW.minusSeconds(1), reservation.id());

        List<Object> outcomes = runTasks(List.of(
                () -> attemptCancel(reservation.id()),
                () -> reservations.expireBatch()));

        assertThat(outcomes).hasSize(2);
        assertThat(status(reservation.id())).isIn("CANCELLED", "EXPIRED");
        assertAccounting(event.id(), 10, 10, 0);
        assertThat(eventCount("ReservationCancelled", event.id()) + eventCount("ReservationExpired", event.id())).isOne();
    }

    @Test
    void expirationRacingNewReservationsPreservesInvariant() throws Exception {
        Event event = createEvent(5);
        Reservation old = reservations.create(event.id(), 5, "old-reservation");
        jdbc.update("UPDATE reservations SET expires_at=? WHERE id=?", NOW.minusSeconds(1), old.id());
        List<Callable<Object>> tasks = new ArrayList<>();
        tasks.add(() -> reservations.expireBatch());
        for (int i = 0; i < 10; i++) {
            int attempt = i;
            tasks.add(() -> {
                try {
                    return reservations.create(event.id(), 1, "replacement-" + attempt);
                } catch (ApplicationException exception) {
                    assertThat(exception.code()).isEqualTo(INSUFFICIENT_CAPACITY);
                    return exception.code();
                }
            });
        }
        runTasks(tasks);

        int available = available(event.id());
        int active = activeQuantity(event.id());
        assertThat(available).isBetween(0, 5);
        assertThat(active).isBetween(0, 5);
        assertThat(available + active).isEqualTo(5);
    }

    @Test
    void failureAfterCapacityAcquisitionRollsBackReservationCapacityIdempotencyAndOutbox() {
        Event event = createEvent(10);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            reservations.create(event.id(), 3, "rolled-back");
            throw new IllegalStateException("simulated failure before response");
        })).isInstanceOf(IllegalStateException.class).hasMessage("simulated failure before response");
        assertAccounting(event.id(), 10, 10, 0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_records WHERE idempotency_key='rolled-back'", Long.class)).isZero();
        assertThat(eventCount("ReservationCreated", event.id())).isZero();
    }

    @Test
    void duplicateDeliveryIsAppliedOnceAndReadModelConverges() {
        Event event = createEvent(10);
        String created = payload("EventCreated", event.id());
        projection.consume(created);
        projection.consume(created);

        reservations.create(event.id(), 3, "projection-operation");
        String reserved = payload("ReservationCreated", event.id());
        projection.consume(reserved);
        projection.consume(reserved);

        assertThat(jdbc.queryForObject("SELECT available_tickets FROM event_availability_projection WHERE event_id=?",
                Integer.class, event.id())).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inbox_events", Long.class)).isEqualTo(2);
    }

    private Object attemptCancel(UUID id) {
        try {
            return reservations.cancel(id);
        } catch (ApplicationException exception) {
            assertThat(exception.code()).isEqualTo(RESERVATION_NOT_CANCELLABLE);
            return exception.code();
        }
    }

    private Event createEvent(int capacity) {
        return transaction.execute(status -> events.create("Concurrency " + UUID.randomUUID(), NOW.plusSeconds(86_400), capacity));
    }

    private void assertAccounting(UUID eventId, int capacity, int available, int active) {
        assertThat(available(eventId)).isEqualTo(available).isBetween(0, capacity);
        assertThat(activeQuantity(eventId)).isEqualTo(active).isLessThanOrEqualTo(capacity);
        assertThat(available(eventId) + activeQuantity(eventId)).isEqualTo(capacity);
    }

    private int available(UUID id) {
        return jdbc.queryForObject("SELECT available_tickets FROM events WHERE id=?", Integer.class, id);
    }

    private int activeQuantity(UUID id) {
        return jdbc.queryForObject("SELECT COALESCE(sum(quantity),0) FROM reservations WHERE event_id=? AND status='PENDING'",
                Integer.class, id);
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM reservations WHERE id=?", String.class, id);
    }

    private long eventCount(String type, UUID aggregateId) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type=? AND aggregate_id=?",
                Long.class, type, aggregateId);
    }

    private String payload(String type, UUID aggregateId) {
        return jdbc.queryForObject("SELECT payload FROM outbox_events WHERE event_type=? AND aggregate_id=?",
                String.class, type, aggregateId);
    }

    private <T> List<T> runConcurrently(int count, java.util.function.IntFunction<Callable<T>> factory) throws Exception {
        List<Callable<T>> tasks = IntStream.range(0, count).mapToObj(factory).toList();
        return runTasks(tasks);
    }

    private <T> List<T> runTasks(List<Callable<T>> tasks) throws Exception {
        CyclicBarrier start = new CyclicBarrier(tasks.size());
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(60, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @TestConfiguration
    static class Dependencies {
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean EventAvailability availability() { return id -> Optional.empty(); }
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }
}
