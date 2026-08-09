# ADR-006: Kafka for Durable Audit Event Streaming

**Status:** Accepted  
**Date:** 2026-08-09

## Context

Every request through the gateway must produce an audit event for security, compliance,
and debugging. The audit record includes: requestId, timestamp, client IP, method, path,
routeId, response status, and outcome (ALLOWED / UNAUTHENTICATED / BLOCKED_WAF / etc.).

The audit path must never block the request path. Writing synchronously to PostgreSQL or
a log file would add latency proportional to the write throughput, creating a bottleneck
under high load.

## Decision

Use **Apache Kafka** for durable audit event streaming.

The `AuditLoggingFilter` (the outermost `WebFilter` in the chain) publishes events to the
`sentinel.audit.requests` topic using a fire-and-forget Kafka producer. Events are
serialized as JSON and published after the response is committed.

A `LoggingAuditEventPublisher` is always active as a fallback (logs to SLF4J at INFO level).
When `sentinel.audit.kafka.enabled=true`, the `KafkaAuditEventPublisher` replaces it.

Kafka is **excluded from `KafkaAutoConfiguration`** to prevent Spring Boot from requiring
a Kafka broker even when `sentinel.audit.kafka.enabled=false`.

## Consequences

**Positive:**
- Audit events are durably stored in Kafka topics with configurable retention
- Consumer applications (SIEM, analytics, alerting) can subscribe without gateway changes
- Fire-and-forget means Kafka latency never adds to request latency
- Multiple consumers can process the same audit stream independently

**Negative:**
- Fire-and-forget means events can be lost if Kafka is unavailable (see FAILURE_MODES.md)
- For strict compliance (e.g., PCI DSS), consider transactional outbox: write to PostgreSQL
  first, then stream with a CDC connector (Debezium)
- KRaft mode (no ZooKeeper) requires Kafka 3.3+; used in Docker Compose and Kubernetes

## Alternatives Considered

- **Synchronous PostgreSQL write** — rejected; adds DB latency to every request
- **Fluentd/Loki log shipping** — rejected; log parsing is fragile; structured events are preferable
- **Amazon Kinesis** — rejected; vendor lock-in; adds cloud provider dependency
