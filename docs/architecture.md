# Arquitetura atual

## Estado encontrado antes da refatoração

A implementação inicial já tinha boas fundações: migrations Flyway, DTOs separados da resposta HTTP, aquisição atômica
de capacidade, expiração com `FOR UPDATE SKIP LOCKED` e gravação de Outbox na transação de negócio. Entretanto, entidades
JPA eram chamadas de domínio e continham anotações de infraestrutura; services recebiam DTOs HTTP e devolviam DTOs;
repositories Spring Data eram usados diretamente pelos casos de uso; serialização JSON fazia parte dos services; e os
packages não expressavam a direção das dependências.

Havia dois riscos concorrentes importantes. A idempotência fazia primeiro uma leitura sem lock e dependia da unique
constraint; a violação podia ocorrer somente no flush do commit, fora do `catch`, e requests simultâneos iguais não
recebiam necessariamente a resposta original. O cancelamento carregava a reserva sem lock, permitindo que duas JVMs
observassem `PENDING` e liberassem a mesma capacidade duas vezes. Além disso, o `LEAST(capacity, ...)` escondia liberações
duplicadas, mas podia tornar ingressos ainda reservados novamente disponíveis. Os testes existentes só verificavam um
método vazio e não iniciavam o contexto. O publisher Outbox já oferecia entrega at-least-once, mas segurava uma transação
durante o envio síncrono ao Kafka. O Compose anunciava apenas `kafka:9092`, endereço inadequado para uma aplicação Java
executada diretamente no host.

## Camadas e direção de dependências

- `domain`: modelos Java sem Spring, HTTP, JPA, Kafka ou serialização. `Event` valida capacidade e disponibilidade;
  `Reservation` concentra as transições únicas de cancelamento e expiração.
- `application`: casos de uso e portas necessárias (`EventRepository`, `ReservationRepository`, `IdempotencyStore` e
  `DomainEventOutbox`). Delimita transações e coordena domínio e persistência sem conhecer MVC, JPA ou Kafka.
- `infrastructure`: entidades JPA e adapters PostgreSQL, persistência da Outbox, publisher Kafka, schedulers e
  configuração. É a camada que implementa as portas da aplicação.
- `interfaces.rest`: controllers MVC, records de request/response, validação Bean Validation, documentação OpenAPI e
  tradução centralizada de falhas para `ProblemDetail`.

A direção principal é `interfaces -> application -> domain`. Infrastructure também depende das portas de application e
do domain para adaptá-las; domain não depende das camadas externas.

## Limites transacionais

- criação de evento: evento e `EventCreated` na Outbox são gravados em uma única transação;
- criação de reserva: lock da chave, validação do retry, aquisição de capacidade, reserva, registro idempotente e
  `ReservationCreated` são uma única transação;
- cancelamento: lock pessimista da reserva, transição, devolução da capacidade e Outbox são uma única transação;
- lote de expiração: seleção bloqueada, transições, devoluções e Outbox são uma única transação curta, limitada a 100;
- consultas são transações read-only;
- publicação Outbox bloqueia até 100 linhas e marca `published_at` na transação somente após confirmação do Kafka.

Não há chamadas HTTP externas em transações. O envio Kafka do publisher permanece síncrono dentro da transação para não
marcar antes do acknowledge; esse trade-off evita perda, mas aumenta o tempo de lock e permite duplicata se o broker
confirmar e o commit falhar.

## Concorrência e prevenção de overselling

A aquisição usa no PostgreSQL um único statement condicional:

```sql
UPDATE events
SET available_tickets = available_tickets - :quantity
WHERE id = :id AND available_tickets >= :quantity
```

Somente um update que afeta uma linha autoriza criar a reserva. O row lock e a reavaliação da condição pelo PostgreSQL
funcionam entre processos e JVMs; não há lock local. Constraints garantem `0 <= available_tickets <= capacity`.
Cancelamento usa `PESSIMISTIC_WRITE`; expiração usa `FOR UPDATE SKIP LOCKED`. Assim, apenas uma instância muda a reserva
de `PENDING` e devolve capacidade. A liberação falha explicitamente caso viole o limite, em vez de mascarar erro.

A disponibilidade retornada por `GET /events/{id}` vem hoje diretamente do write model e é fortemente consistente após o
commit. Uma projeção eventualmente consistente é uma evolução, não uma funcionalidade já implementada.

## Idempotência

`POST /events/{id}/reservations` exige chave não vazia de no máximo 160 caracteres. Dentro da transação, um
`pg_advisory_xact_lock(hashtextextended(key, 0))` serializa a chave entre todas as instâncias. Depois do lock:

1. chave inexistente: executa a operação e persiste chave, SHA-256 de `eventId:quantity` e id da reserva;
2. mesma chave e mesmo hash: retorna a reserva original, inclusive com seu estado atual, sem consumir capacidade nem
   criar novo evento Outbox;
3. mesma chave e payload lógico diferente: retorna `409 IDEMPOTENCY_CONFLICT`.

A unique constraint permanece como defesa adicional. O lock é liberado automaticamente no commit/rollback. O registro
não expira atualmente; definição de retenção fica para uma fase posterior.

## Outbox e Kafka

Cada mutação de negócio persiste um envelope JSON com identificadores, tipo, instante e dados na mesma transação local.
O scheduler seleciona linhas pendentes com `FOR UPDATE SKIP LOCKED`, publica no tópico configurável
`flash-booking.events` e só então preenche `published_at`. Múltiplos publishers podem trabalhar sem selecionar a mesma
linha. A semântica é at-least-once: consumidores futuros precisam deduplicar pelo `eventId` do envelope.

Não existem ainda consumer, Inbox, read model, CQRS ou Event Store. Kafka é integração assíncrona; não é a fonte de
verdade da reserva.

## Expiração

A cada intervalo configurável, cada instância procura até 100 reservas `PENDING` vencidas. PostgreSQL distribui trabalho
com `SKIP LOCKED`. A transição de domínio também verifica status e prazo. A mesma transação persiste `EXPIRED`, devolve a
capacidade exatamente uma vez e grava `ReservationExpired`. Nenhum estado em memória controla o processamento.

## Erros e contrato HTTP

`@RestControllerAdvice` produz `application/problem+json` sem SQL ou stack trace: validação/malformed request é `400`;
recursos ausentes são `404`; conflito de idempotência ou estado é `409`; capacidade insuficiente é `422`; falhas não
mapeadas são `500` com mensagem pública genérica e log interno. Os cinco endpoints e respostas relevantes estão no
OpenAPI/Swagger.

## Próximas fases (não implementadas)

- consumer Kafka idempotente e Inbox;
- projeção/read model CQRS para disponibilidade eventual;
- Event Store e reconstrução orientada a eventos, caso o produto realmente os exija;
- estratégia de claim/publicação Outbox que não mantenha transação aberta durante I/O, com retry/backoff e dead letter;
- retenção/limpeza de idempotência e Outbox;
- testes de integração concorrentes com PostgreSQL/Kafka reais via Testcontainers;
- observabilidade distribuída, métricas operacionais e testes de carga versionados por cenário.
