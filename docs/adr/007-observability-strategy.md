# ADR-007: Local observability strategy

## Status

Accepted

## Context

Operators need to connect an HTTP reservation to its asynchronous projection, distinguish business rejection from system
failure, and assess PostgreSQL, Kafka, Outbox, and consumer state. The technical exercise must remain runnable locally
without a mandatory monitoring stack or SaaS account.

## Decision

Use Spring Boot Actuator for a deliberately limited health/info/metrics surface, Micrometer for low-cardinality business
and pipeline meters, and SLF4J MDC plus structured key/value messages for investigation. Propagate a UUID correlation ID
from HTTP through the persisted event envelope to the Kafka consumer. Readiness includes PostgreSQL and Kafka; liveness
only represents the application process. Expose Prometheus text format as an optional integration point, without running
a Prometheus server.

## Consequences

The application answers common operational questions on a laptop and is ready for an external scraper or log shipper.
Unique identifiers stay in logs rather than metric tags. A bounded Kafka metadata request makes readiness meaningful.
Correlation is explicit but does not provide cross-service spans or sampling. Deployment owners remain responsible for
access control around Actuator endpoints when exposing the service outside a trusted local network.

## Alternatives Considered

A required Grafana/Prometheus/ELK/Loki stack was rejected as disproportionate local infrastructure. A SaaS agent was
rejected because it creates credentials, cost, and vendor coupling. OpenTelemetry tracing was deferred until multiple
services make span context materially more useful than the current correlation ID.
