# ADR-006: Controle de concorrência no PostgreSQL

## Status

Accepted

## Context

Reservas, retries, cancelamentos e expirações chegam por várias JVMs. Locks locais não protegem o inventário
compartilhado
e `SELECT` seguido de `UPDATE` permitiria overselling.

## Decision

O PostgreSQL é o ponto de serialização. A aquisição executa um único `UPDATE ... WHERE available_tickets >= :quantity
RETURNING ...`; cada mudança incrementa a versão do evento. A chave idempotente é serializada por
`pg_advisory_xact_lock(hashtextextended(key, 0))` e continua protegida por `UNIQUE`. Cancelamento bloqueia a reserva com
`FOR UPDATE`; workers de expiração adquirem lotes com `FOR UPDATE SKIP LOCKED`. Estado, capacidade, registro idempotente
e Outbox compartilham a transação `READ COMMITTED` padrão.

## Alternatives Considered

`synchronized` foi rejeitado por ser local à JVM. Redis adicionaria infraestrutura sem melhorar a atomicidade do write
model. `SERIALIZABLE` global reduziria concorrência e não é necessário. Optimistic locking exigiria retry sob contenção.
Pessimistic locking é usado somente no ciclo terminal da reserva; o hot path usa o update condicional atômico.

## Consequences

Instâncias compartilham garantias sem estado crítico em memória. Operações para a mesma chave ou linha aguardam
brevemente;
eventos diferentes continuam concorrentes. Constraints impedem disponibilidade negativa ou acima da capacidade mesmo se
um defeito ultrapassar a camada de aplicação.
