# Code review guide

| Topic | Primary implementation |
|---|---|
| Anti-oversell | `PostgresEventRepository.reserveCapacity`, migrations `V1`/`V2`, `ReservationConcurrencyIT` |
| Idempotency | `ReservationUseCases.createReservation`, `PostgresIdempotencyStore`, `RequestFingerprint` |
| Transaction boundary | `EventUseCases`, `ReservationUseCases`, `JpaDomainEventOutbox` (`@Transactional`) |
| Transactional Outbox | `JpaDomainEventOutbox`, `OutboxClaims`, `OutboxPublisher`, `JpaOutboxRepository` |
| Kafka | `KafkaConfiguration`, `application.yml`, `DomainEvent` |
| Inbox | `AvailabilityProjectionConsumer`, table `inbox_events` in migration `V3` |
| CQRS / eventual consistency | `PostgresEventAvailability`, `AvailabilityProjectionConsumer`, `EventUseCases.getAvailability` |
| Expiration | `ReservationExpirationScheduler`, `ReservationUseCases.expireBatch`, expiration index in `V1` |
| Cancellation/expiration races | `findByIdForUpdate`, `findExpiredForUpdate`, `ReservationConcurrencyIT` |
| HTTP/errors | controllers, DTOs, `ApiExceptionHandler`, `CorrelationIdFilter` |
| Tests | `src/test`, `pom.xml`, `load-tests/flash-sale.js` |
| Observability | `ApplicationConfiguration`, `KafkaHealthIndicator`, metrics/logging in use cases and messaging |

## Trade-offs worth discussing

- Atomic PostgreSQL capacity updates provide strong correctness and simple recovery, but a very popular event is a hot row.
- Outbox commits facts with business state, but adds polling latency and operational backlog.
- At-least-once delivery avoids pretending to offer end-to-end exactly-once; Inbox and version checks add consumer state.
- The availability projection scales/decouples reads, but exposes eventual consistency to clients.
