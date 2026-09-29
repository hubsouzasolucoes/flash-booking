# Observability

The application uses Spring Boot Actuator, Micrometer, and searchable key/value logs. This keeps local operation simple
while allowing a deployment to scrape Prometheus format or ship logs later without changing business code.

## Correlation

`X-Correlation-Id` accepts a UUID. A valid caller value is reused; an absent or malformed value is replaced with a new
UUID. The response always returns the effective value. During HTTP handling it is held in the MDC and removed in a
`finally` block.

Commands copy the effective ID into the domain-event envelope stored by the transactional Outbox. The Kafka consumer
reads it back into MDC for Inbox and projection processing and removes it afterward. Consequently the implemented path is
HTTP → application command → domain-event envelope → Outbox → Kafka → consumer → Inbox/projection. Events produced by
the expiration scheduler receive a new correlation ID because no originating HTTP request exists. There currently are no
derived domain events, so `causationId` remains optional and empty.

## Logging

Every log line carries the MDC `correlationId` in the configured pattern. Event-oriented messages use stable fields such
as `event`, `eventId`, `aggregateId`, `eventType`, and `reservationId` when relevant. INFO is reserved for batch or
operational outcomes; per-event projection and Outbox creation details are DEBUG. WARN reports publication retry,
consumer retry, and DLT routing. The REST boundary logs unexpected failures once at ERROR. Expected capacity and
idempotency conflicts do not produce stack traces. Payloads, headers, credentials, connection strings, and complete
idempotency keys are not logged.

## Metrics

Actuator exposes meters at `/actuator/metrics/{name}` and Prometheus format at `/actuator/prometheus`.

| Meter | Type | Tags | Meaning |
|---|---|---|---|
| `booking.reservation.created` | counter | none | reservations committed |
| `booking.reservation.rejected` | counter | `reason=insufficient_capacity\|idempotency_conflict` | expected rejection |
| `booking.reservation.cancelled` | counter | none | successful terminal transition |
| `booking.reservation.expired` | counter | none | expired reservations |
| `booking.reservation.idempotent.replay` | counter | none | repeated logical requests |
| `booking.reservation.duration` | timer | none | reservation command latency |
| `booking.idempotency.created` | counter | none | new idempotency records |
| `booking.idempotency.replay` | counter | none | matching-key replay |
| `booking.idempotency.conflict` | counter | none | key/payload conflicts |
| `booking.expiration.batch.duration` | timer | none | expiration worker duration |
| `booking.outbox.pending` | gauge | none | PENDING plus PROCESSING backlog |
| `booking.outbox.published` | counter | none | broker-acknowledged publications |
| `booking.outbox.failed` | counter | none | publication attempts that failed |
| `booking.outbox.retry` | counter | none | retries scheduled with backoff |
| `booking.consumer.processed` | counter | `consumer=availability-projection-v1` | applied deliveries |
| `booking.consumer.duplicate` | counter | `consumer=availability-projection-v1` | Inbox duplicates |
| `booking.consumer.failed` | counter | `consumer=availability-projection-v1` | handler failures |
| `booking.consumer.retry` | counter | `consumer=availability-projection-v1` | retry attempts |
| `booking.consumer.dlt` | counter | `consumer=availability-projection-v1` | records routed to DLT |

The `reason` and `consumer` values come from closed, code-defined sets. UUIDs, correlation IDs, reservation IDs, event
IDs, and idempotency keys are deliberately never metric tags. Standard `http.server.requests`, JVM, datasource, and
Kafka client meters remain available and are not reimplemented.

## Health

Only `health`, `info`, `metrics`, and `prometheus` are exposed over HTTP.

* `/actuator/health` reports the aggregate, including PostgreSQL and Kafka.
* `/actuator/health/liveness` checks application process state and deliberately does not restart the process for a
  transient broker/database outage.
* `/actuator/health/readiness` includes application readiness, PostgreSQL, and Kafka; the Compose application healthcheck
  uses this endpoint before allowing the proxy to start.
* `/actuator/info` identifies the application and version.

Health details do not include credentials. The Kafka check performs a bounded cluster metadata request.

## Troubleshooting

* **A reservation failed unexpectedly:** copy `correlationId` from the response/header and search application logs. Then
  use `reservationId`, `eventId`, and `eventType` fields to narrow the flow.
* **The read model looks stale:** inspect `booking.outbox.pending`, `booking.consumer.failed`,
  `booking.consumer.retry`, and `booking.consumer.dlt`; search Outbox retry and consumer logs.
* **The Outbox is growing:** check readiness/Kafka health and compare `booking.outbox.failed` and
  `booking.outbox.published`. Retry logs contain attempt and backoff, but never broker credentials.
* **A projection is not converging:** inspect consumer failure/DLT counters and ordering-gap retry logs, correct the
  missing sequence, and reprocess the DLT according to the operational procedure.
* **Capacity rejections are increasing:** query `booking.reservation.rejected` with
  `reason=insufficient_capacity`; this is an expected flash-sale outcome rather than an application ERROR.

Distributed tracing is not introduced for this monolith. If the system is split into services, OpenTelemetry trace
context and a collector can complement—not replace—the correlation contract.
