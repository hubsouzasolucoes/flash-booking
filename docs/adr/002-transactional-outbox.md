# ADR-002: Transactional Outbox com lease

## Status

Accepted

## Context

Dual write PostgreSQL/Kafka pode perder eventos e publishers concorrentes não podem duplicar trabalho normalmente.

## Decision

Persistir Outbox com o negócio; reivindicar usando `SKIP LOCKED`, status e lease, e fazer I/O fora da transação de
claim.
Retry usa backoff exponencial limitado.

## Consequences

Falha do Kafka não desfaz o negócio. Crash após publish pode duplicar, portanto consumidores precisam de Inbox.

## Alternatives Considered

Transação longa durante Kafka foi rejeitada por contenção; XA foi rejeitado por complexidade.
