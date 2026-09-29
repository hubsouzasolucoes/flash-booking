# Architecture

## Write path

Client -> Spring Boot -> transactional PostgreSQL write -> Outbox -> Kafka.

## Reservation invariant

`available_tickets` must never become negative. The invariant is enforced by the database statement used to acquire
capacity, not by JVM locks.

## Delivery semantics

Database changes and outbox insertion share one local transaction. Kafka publication is asynchronous and retried by the
publisher. Consumers must be idempotent because delivery is at-least-once.
