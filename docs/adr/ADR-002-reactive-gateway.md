# ADR-002: Use Reactive (WebFlux-Based) Spring Cloud Gateway

**Status:** Accepted  
**Date:** 2026-08-08  
**Deciders:** Pulkit Girdhar

---

## Context

Spring Cloud Gateway is available in two flavours:
- **Reactive** (default) — built on Spring WebFlux, Project Reactor, Netty
- **MVC** (Spring Cloud Gateway Server MVC) — introduced in 2023.0.0, built on Spring MVC and servlet containers

---

## Decision

Use the **reactive** (WebFlux-based) variant.

---

## Rationale

The gateway is an I/O-bound proxy. Its primary work is:
1. Validate tokens (I/O: JWKS endpoint or Redis cache)
2. Check rate limits (I/O: Redis)
3. Proxy the request (I/O: downstream service)

In a reactive model, a single thread can handle many concurrent in-flight requests while all I/O operations are in progress. This is the ideal model for a proxy.

A servlet-based (MVC) gateway would require one thread per in-flight request, limiting concurrency under load without significantly reducing complexity for this use case.

The reactive model is also better documented, more mature, and has broader community support for production API gateway use cases.

---

## Consequences

- All filter logic must be written as `Mono`/`Flux` reactive chains.
- Testing uses `WebTestClient` (reactive) rather than `MockMvc`.
- Downstream test services can use Spring MVC (they are simple and not latency-critical).
- Blocking calls (e.g., synchronous JDBC) must not be made on the event loop — use `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` if needed.
