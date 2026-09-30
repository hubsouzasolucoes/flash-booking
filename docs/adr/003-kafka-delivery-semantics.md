# ADR-003: Kafka at-least-once

## Situação

Aceita

## Contexto

Não existe commit atômico entre o broker, Outbox e offset do consumer.

## Decisão

Usar `booking.domain-events`, key `aggregateId`, producer idempotente com `acks=all`, Inbox e DLT após retries
limitados.

## Consequências

Há ordem por agregado e duplicatas são normais. Exactly-once end-to-end não é prometido.

## Alternativas consideradas

Key aleatória perderia ordenação; Schema Registry e Kafka transactions não eliminariam atomicidade com PostgreSQL.
