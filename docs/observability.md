# Observabilidade

A aplicação usa Spring Boot Actuator, Micrometer e logs pesquisáveis em pares chave/valor. Isso simplifica a operação
local e permite coletar o formato Prometheus ou enviar logs futuramente sem mudar o código de negócio.

## Correlação

`X-Correlation-Id` aceita um UUID. Um valor válido do cliente é reutilizado; valor ausente ou inválido é substituído. A
resposta sempre devolve o valor efetivo, mantido no MDC durante o HTTP e removido em um bloco `finally`.

Os comandos copiam esse ID para o envelope persistido pela Outbox. O consumidor Kafka o restaura no MDC durante Inbox e
projeção. O caminho implementado é HTTP → comando → envelope → Outbox → Kafka → consumidor → Inbox/projeção. Eventos do
agendador de expiração recebem um novo ID, pois não há requisição HTTP de origem. `causationId` permanece opcional e vazio.

## Logs

Cada linha contém `correlationId` no MDC. Mensagens usam campos estáveis como `event`, `eventId`, `aggregateId`,
`eventType` e `reservationId`. INFO registra resultados operacionais; detalhes por evento ficam em DEBUG; WARN registra
retry e DLT; a fronteira REST registra falhas inesperadas uma vez em ERROR. Conflitos esperados não geram stack trace.
Payloads, headers, credenciais, strings de conexão e chaves idempotentes completas não são registrados.

## Métricas

O Actuator expõe métricas em `/actuator/metrics/{name}` e no formato Prometheus em `/actuator/prometheus`.

| Métrica | Tipo | Tags | Significado |
|---|---|---|---|
| `booking.reservation.created` | contador | nenhuma | reservas confirmadas |
| `booking.reservation.rejected` | contador | `reason=insufficient_capacity\|idempotency_conflict` | rejeição esperada |
| `booking.reservation.cancelled` | contador | nenhuma | cancelamento concluído |
| `booking.reservation.expired` | contador | nenhuma | reservas expiradas |
| `booking.reservation.idempotent.replay` | contador | nenhuma | requisições lógicas repetidas |
| `booking.reservation.duration` | temporizador | nenhuma | latência do comando de reserva |
| `booking.idempotency.created` | contador | nenhuma | novos registros idempotentes |
| `booking.idempotency.replay` | contador | nenhuma | repetição com chave correspondente |
| `booking.idempotency.conflict` | contador | nenhuma | conflitos de chave/payload |
| `booking.expiration.batch.duration` | temporizador | nenhuma | duração do worker de expiração |
| `booking.outbox.pending` | medidor | nenhuma | backlog PENDING e PROCESSING |
| `booking.outbox.published` | contador | nenhuma | publicações confirmadas pelo broker |
| `booking.outbox.failed` | contador | nenhuma | tentativas de publicação com falha |
| `booking.outbox.retry` | contador | nenhuma | novas tentativas agendadas |
| `booking.consumer.processed` | contador | `consumer=availability-projection-v1` | entregas aplicadas |
| `booking.consumer.duplicate` | contador | `consumer=availability-projection-v1` | duplicatas da Inbox |
| `booking.consumer.failed` | contador | `consumer=availability-projection-v1` | falhas do handler |
| `booking.consumer.retry` | contador | `consumer=availability-projection-v1` | novas tentativas |
| `booking.consumer.dlt` | contador | `consumer=availability-projection-v1` | registros enviados à DLT |

Os valores de `reason` e `consumer` vêm de conjuntos fechados definidos no código. UUIDs e IDs não são tags. As métricas
padrão de HTTP, JVM, datasource e clientes Kafka continuam disponíveis.

## Saúde

Somente `health`, `info`, `metrics` e `prometheus` são expostos via HTTP.

- `/actuator/health` agrega PostgreSQL e Kafka.
- `/actuator/health/liveness` representa o processo e não o reinicia por falha transitória externa.
- `/actuator/health/readiness` inclui aplicação, PostgreSQL e Kafka e é usado pelo healthcheck do Compose.
- `/actuator/info` identifica aplicação e versão.

Os detalhes não incluem credenciais. A verificação do Kafka faz uma consulta limitada aos metadados do cluster.

## Diagnóstico

- **Uma reserva falhou:** pesquise nos logs o `correlationId` da resposta e refine por `reservationId`, `eventId` e
  `eventType`.
- **A projeção está atrasada:** inspecione `booking.outbox.pending`, `booking.consumer.failed`,
  `booking.consumer.retry` e `booking.consumer.dlt`.
- **A Outbox cresce:** confira prontidão/Kafka e compare `booking.outbox.failed` com `booking.outbox.published`.
- **A projeção não converge:** corrija a sequência ausente e reprocesse a DLT conforme o procedimento operacional.
- **Rejeições aumentaram:** consulte `booking.reservation.rejected` com `reason=insufficient_capacity`; esse é um
  resultado esperado da venda relâmpago, não um ERROR.

Tracing distribuído não foi introduzido neste monólito. Se ele for dividido, OpenTelemetry pode complementar, e não
substituir, o contrato de correlação.
