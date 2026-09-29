# Flash Booking

API de reserva de ingressos para flash sales, implementada com Java 21, Spring Boot, PostgreSQL, Kafka, Flyway e
Transactional Outbox, Inbox e uma projeção CQRS de disponibilidade. O write model relacional é a fonte autoritativa;
Kafka transporta eventos com entrega **at-least-once** e não há dependência de cloud.

## Executar do zero

Pré-requisitos: Git, Java 21 e Docker com Docker Compose. Para subir aplicação e toda a infraestrutura local:

```bash
docker compose up --build
```

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`

Para remover também os dados locais: `docker compose down -v`.

## Endpoints

- `POST /events`
- `GET /events/{id}`
- `POST /events/{id}/reservations` (`Idempotency-Key`, de 1 a 160 caracteres, obrigatório)
- `GET /reservations/{id}`
- `DELETE /reservations/{id}`

Exemplo:

```bash
curl -X POST http://localhost:8080/events \
  -H 'Content-Type: application/json' \
  -d '{"name":"Java Conference","startsAt":"2027-10-01T18:00:00Z","capacity":100}'

curl -X POST http://localhost:8080/events/SEU_EVENT_ID/reservations \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: checkout-123-attempt-1' \
  -d '{"quantity":2}'
```

## Desenvolvimento

Com PostgreSQL e Kafka acessíveis nas portas padrão (o próprio Compose os expõe):

```bash
mvn test          # suíte rápida: unidade, API isolada e arquitetura
mvn clean verify  # quality gate completo, incluindo Testcontainers e JaCoCo
mvn spring-boot:run
```

Configurações variáveis aceitam `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_MAX_POOL_SIZE`, `DB_MIN_IDLE`,
`DB_CONNECTION_TIMEOUT_MS`, `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_EVENTS_TOPIC`,
`RESERVATION_TTL`, `OUTBOX_FIXED_DELAY_MS`, `EXPIRATION_FIXED_DELAY_MS` e `SERVER_PORT`.

`GET /events/{id}` lê `event_availability_projection`: logo após um comando ele pode retornar a versão anterior (ou 404
durante a criação) até Outbox → Kafka → Inbox convergir. A concessão de ingressos nunca consulta essa projeção; ela usa
um `UPDATE` condicional no PostgreSQL. Para acompanhar o fluxo, use `docker compose logs -f app` e procure por
`outbox event created`, `outbox event published`, `projection updated` e `duplicate ignored`. Métricas ficam em
`/actuator/metrics`.

A arquitetura está em [`docs/architecture.md`](docs/architecture.md), com detalhes em
[`docs/event-driven-architecture.md`](docs/event-driven-architecture.md). O diretório
`load-tests` contém um cenário k6 opcional para concorrência.

## Observability

The local operational surface is intentionally small: [health](http://localhost:8080/actuator/health),
[liveness](http://localhost:8080/actuator/health/liveness),
[readiness](http://localhost:8080/actuator/health/readiness),
[metrics](http://localhost:8080/actuator/metrics), and
[Prometheus exposition](http://localhost:8080/actuator/prometheus). API discovery is available through
[Swagger UI](http://localhost:8080/swagger-ui.html) and [OpenAPI JSON](http://localhost:8080/v3/api-docs).

Clients may send a UUID in `X-Correlation-Id`; otherwise the API generates one. The value is returned in every response
and included in error bodies and asynchronous domain-event envelopes. See
[`docs/observability.md`](docs/observability.md) for metric names, log fields, health semantics, and troubleshooting.

## Testing

`mvn test` executa a pirâmide rápida; `mvn clean verify` acrescenta integração e concorrência reais com PostgreSQL,
as migrations Flyway e gera `target/site/jacoco/index.html`. Docker precisa estar disponível para a suíte completa.
A estratégia, os invariantes cobertos e o comando de carga via Compose estão em [`docs/testing.md`](docs/testing.md).

## Concurrency & Consistency

O PostgreSQL impede overselling por update condicional atômico e constraints. Advisory locks transacionais mais a chave
única tornam `Idempotency-Key` segura entre instâncias; locks de linha tornam cancelamento e expiração single-winner. O
read model é eventualmente consistente e nunca concede capacidade. Detalhes e semântica HTTP estão em
[`docs/concurrency.md`](docs/concurrency.md).

Para uma demonstração balanceada: `docker compose up --build --scale app=3`. Depois de criar o evento, execute
`EVENT_ID=<uuid> k6 run load-tests/flash-sale.js`. Respostas 422 representam esgotamento esperado, não erro técnico.
