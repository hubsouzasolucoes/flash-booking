# ADR 003 — Kafka at-least-once

## Context
Não existe commit atômico entre o broker, Outbox e offset do consumer.

## Decision
Usar `booking.domain-events`, key `aggregateId`, producer idempotente com `acks=all`, Inbox e DLT após retries limitados.

## Consequences
Há ordem por agregado e duplicatas são normais. Exactly-once end-to-end não é prometido.

## Alternatives considered
Key aleatória perderia ordenação; Schema Registry e Kafka transactions não eliminariam atomicidade com PostgreSQL.
