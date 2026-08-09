# ADR-004: PostgreSQL with R2DBC for Production Persistence

**Status:** Accepted  
**Date:** 2026-08-09

## Context

The gateway needs durable storage for API keys and security policies. The routing engine
is reactive (WebFlux/Netty); blocking JDBC calls on the reactive event loop would cause
thread starvation under load.

H2 in-memory was used in phases 1–18 for development simplicity. Phase 19 (Docker Compose)
requires a production-grade database that persists data across restarts.

## Decision

Use **PostgreSQL 16** as the production database, accessed via **Spring Data R2DBC**.

R2DBC is the reactive SQL driver specification. Spring's R2DBC support integrates cleanly
with Project Reactor; all database operations return `Mono<T>` or `Flux<T>` and execute
on R2DBC's non-blocking I/O threads.

H2 continues to be used in development and tests (R2DBC H2 dialect, `schema.sql` auto-init).

## Consequences

**Positive:**
- Non-blocking I/O throughout the full request path (no thread pool exhaustion under load)
- PostgreSQL provides ACID semantics, `BIGSERIAL` PKs, `TIMESTAMPTZ`, full SQL capabilities
- Schema migration is explicit (`infrastructure/postgres/init.sql` for Docker; Flyway/Liquibase for CI)
- R2DBC connection pool auto-reconnects on PostgreSQL failover

**Negative:**
- H2/PostgreSQL dialect differences require separate DDL (`AUTO_INCREMENT` vs `BIGSERIAL`);
  `SPRING_SQL_INIT_MODE=never` is set in Docker to prevent H2-specific `schema.sql` from
  running against PostgreSQL
- R2DBC does not support JPA/Hibernate; all queries are explicit (repository methods or raw SQL)
- `@Transactional` works differently in reactive context (must use `TransactionalOperator`)

## Alternatives Considered

- **MongoDB** — rejected; relational data model fits API keys and policies better
- **JDBC + virtual threads** (Project Loom) — rejected; requires JDK 21; project targets JDK 17
- **R2DBC + H2 in all environments** — rejected; H2's PostgreSQL compatibility mode is incomplete
