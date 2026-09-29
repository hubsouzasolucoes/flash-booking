# Flash Booking

Case de backend para reserva de ingressos em flash sale. Implementado em Java 21 e Spring Boot, com PostgreSQL, Kafka, Transactional Outbox, idempotência e controle atômico de capacidade.

## Executar

Pré-requisito: Docker (Java 21/Maven só são necessários se quiser executar fora do container).

```bash
docker compose up --build
```

API: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI: `http://localhost:8080/v3/api-docs`  
Health: `http://localhost:8080/actuator/health`

## Endpoints

- `POST /events`
- `GET /events/{id}`
- `POST /events/{id}/reservations` (`Idempotency-Key` obrigatório)
- `GET /reservations/{id}`
- `DELETE /reservations/{id}`

## Decisões principais

### Zero oversell
A reserva de capacidade usa um único `UPDATE ... WHERE available_tickets >= quantity`. A decisão é tomada atomicamente pelo PostgreSQL e continua válida com múltiplas instâncias da API.

### Idempotência
Cada reserva exige `Idempotency-Key`. A chave é persistida com SHA-256 do payload lógico. Retry com a mesma chave e mesmo payload retorna a reserva original; payload diferente gera `409`.

### Expiração
Reservas nascem `PENDING` e expiram após 10 minutos. Workers usam `FOR UPDATE SKIP LOCKED`, permitindo múltiplas instâncias sem processar a mesma reserva simultaneamente.

### Event-driven e Outbox
Mudanças de negócio geram registros na outbox na mesma transação do estado. Um publisher envia os eventos para Kafka e só então marca o registro como publicado. A entrega é tratada como at-least-once.

### Consistência
A decisão de capacidade é fortemente consistente no write model. Kafka/outbox fornece a base para projeções eventualmente consistentes sem colocar a integridade da reserva no broker.

## Estrutura

`controller` expõe HTTP; `service` contém casos de uso; `domain` modela estado; `repository` isola persistência; `worker` contém processos assíncronos. DTOs não expõem entidades JPA.

## Testes e carga

```bash
mvn test
```

O diretório `load-tests` contém um cenário k6 para concorrência. k6 não é necessário para executar a aplicação.

## Trade-offs

Para manter o case executável localmente, PostgreSQL e Kafka rodam no Docker Compose. Não há dependência de cloud. A implementação usa estado relacional autoritativo + eventos/outbox, em vez de depender exclusivamente de replay de eventos para reconstruir cada aggregate; isso reduz complexidade operacional sem abrir mão de auditabilidade e integração orientada a eventos.

## Evoluções

- read model dedicado para disponibilidade;
- consumer idempotente e tabela de inbox;
- tracing OpenTelemetry;
- métricas Prometheus/Grafana;
- testes de integração completos com Testcontainers;
- particionamento e tuning do event store/outbox para volumes extremos.
