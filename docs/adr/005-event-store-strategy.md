# ADR-005: Não adotar Event Store/Event Sourcing

## Status

Accepted

## Context
O domínio já possui modelo relacional seguro; reconstrução integral aumentaria risco e escopo.

## Decision
Preservar estado relacional autoritativo mais eventos de domínio, Outbox e Kafka. Não há Event Store nesta fase.

## Consequences
Histórico Kafka/Outbox não é API de reconstrução de agregados. Event Sourcing só será reavaliado com necessidade real.

## Alternatives Considered
Event Sourcing completo foi rejeitado como reescrita desnecessária; chamar Outbox de Event Store seria incorreto.
