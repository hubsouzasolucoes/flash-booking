# Infraestrutura orientada a eventos

## Modelo e envelope

São publicados `EventCreated`, `ReservationCreated`, `ReservationCancelled` e `ReservationExpired`. Os três últimos
representam simultaneamente a mudança de reserva e seu efeito já confirmado na disponibilidade. O envelope JSON contém
`eventId`, `eventType`, `eventVersion`, `aggregateId`, `aggregateType`, `aggregateVersion`, `occurredAt`,
`correlationId`, `causationId` opcional e `payload`. A versão de schema atual é 1. Um consumidor futuro deve despachar
por `(eventType,eventVersion)` e manter leitores v1 durante uma migração para v2.

O agregado de ordenação é o evento comercial (`aggregateId=eventId`) e a key Kafka é esse ID. A versão cresce no mesmo
update atômico que altera capacidade. Isso preserva a ordem por evento sem impor ordem global.

## Transactional Outbox

O caso de uso grava estado e envelope na mesma transação. O publisher reivindica até 100 registros com
`FOR UPDATE SKIP LOCKED`, marca `PROCESSING` e um lease de um minuto, e confirma a transação antes do I/O de rede.
Somente o primeiro registro não publicado de cada agregado pode ser reivindicado. Após o acknowledge do broker ele é
marcado `PUBLISHED`; falha devolve a `PENDING` com backoff exponencial limitado a cinco minutos, contador e erro
truncado. Crash libera implicitamente pelo lease.

Há uma janela inevitável entre publish e marcação: duplicatas são esperadas. A garantia é **at-least-once**, nunca
exactly-once end-to-end.

## Kafka, falhas e DLT

O tópico é `booking.domain-events` (seis partitions) e a DLT é `booking.domain-events.dlt`. O producer usa JSON textual,
`acks=all` e idempotência do producer. O consumer tenta novamente duas vezes com intervalo de um segundo; envelope
inválido, versão desconhecida ou gap persistente segue então para DLT, sem bloquear indefinidamente a partition.

## Inbox, projeção e ordering

O consumer insere `(event_id, consumer)` na Inbox e atualiza a projeção na mesma transação. Conflito significa replay e
é ignorado. Updates aceitam apenas `incomingVersion = lastVersion + 1`; versões antigas não regridem a projeção. Gap é
falha observável e vai a retry/DLT. A recuperação operacional é corrigir/republicar a sequência e reprocessar a DLT ou
reconstruir a projeção a partir do write model; reconstrução automática não faz parte desta fase.

`GET /events/{id}` usa a projeção e pode estar temporariamente atrasado. A operação de reserva usa somente o update
condicional `available_tickets >= quantity`, protegido e validado por constraints no PostgreSQL.

## Idempotência e observabilidade

`Idempotency-Key` serializa retries HTTP via advisory lock; `eventId` identifica o fato; `aggregateVersion` ordena
fatos;
Inbox deduplica entrega Kafka; offset apenas controla posição do grupo. Logs estruturados por IDs cobrem criação,
publicação, retry, consumo, duplicata e projeção. Actuator expõe `outbox.pending`, `outbox.publish.*` e `consumer.*`.
