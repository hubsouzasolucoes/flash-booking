# Guia de revisão de código

| Tema | Implementação principal |
|---|---|
| Proteção contra venda excessiva | `PostgresEventRepository.reserveCapacity`, migrations `V1`/`V2`, `ReservationConcurrencyIT` |
| Idempotência | `ReservationUseCases.createReservation`, `PostgresIdempotencyStore`, `RequestFingerprint` |
| Limite transacional | `EventUseCases`, `ReservationUseCases`, `JpaDomainEventOutbox` (`@Transactional`) |
| Outbox transacional | `JpaDomainEventOutbox`, `OutboxClaims`, `OutboxPublisher`, `JpaOutboxRepository` |
| Kafka | `KafkaConfiguration`, `application.yml`, `DomainEvent` |
| Inbox | `AvailabilityProjectionConsumer`, tabela `inbox_events` na migration `V3` |
| CQRS/consistência eventual | `PostgresEventAvailability`, `AvailabilityProjectionConsumer`, `EventUseCases.getAvailability` |
| Aprovação automática | `ReservationApprovalScheduler`, `ReservationUseCases.approveBatch`, índice de status/instante em `V1` |
| Disputas entre cancelamento e aprovação | `findByIdForUpdate`, `findPendingForApproval`, `SKIP LOCKED` |
| HTTP e erros | controllers, DTOs, `ApiExceptionHandler`, `CorrelationIdFilter` |
| Testes | `src/test`, `pom.xml`, `load-tests/flash-sale.js` |
| Observabilidade | `ApplicationConfiguration`, `KafkaHealthIndicator`, métricas e logs nos casos de uso e mensageria |

## Compromissos arquiteturais a discutir

- Atualizações atômicas de capacidade no PostgreSQL oferecem correção forte e recuperação simples, mas um evento muito
  popular concentra contenção em uma linha.
- A Outbox confirma os fatos junto com o estado de negócio, mas acrescenta latência de polling e backlog operacional.
- A entrega pelo menos uma vez evita prometer semântica exatamente uma vez de ponta a ponta; Inbox e verificações de
  versão acrescentam estado ao consumidor.
- A projeção de disponibilidade desacopla e escala leituras, mas expõe consistência eventual aos clientes.
