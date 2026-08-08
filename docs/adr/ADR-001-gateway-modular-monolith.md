# ADR-001: Gateway as Modular Monolith, Not Security Microservices

**Status:** Accepted  
**Date:** 2026-08-08  
**Deciders:** Pulkit Girdhar

---

## Context

API gateways can be decomposed in two ways:

**Option A — Security microservices:**
```
Gateway → Authentication Service → Authorization Service → Rate Limit Service → Routing Service
```

**Option B — Modular monolith (single deployable unit):**
```
Gateway [auth module | authz module | rate-limit module | routing module]
```

The choice affects latency, operational complexity, and failure modes.

---

## Decision

Implement all security and traffic-management logic **inside the gateway process** using clean package boundaries (Option B).

---

## Rationale

| Concern | Microservices | Modular Monolith |
|---|---|---|
| Latency | Added network hops per security check | In-process, no additional I/O |
| Failure modes | Each service is a new failure point | Single failure domain |
| Operational complexity | Deploy/scale 4+ services independently | Deploy one service |
| Shared state | Distributed state coordination required | Shared JVM memory |
| Testing | Service mocking required for unit tests | Direct unit tests |

For an API gateway, the authentication and authorization checks happen on every single request. Adding network round-trips for each check would directly impact P95/P99 latency at scale.

External infrastructure (Keycloak, Redis, PostgreSQL, Kafka) handles responsibilities that genuinely belong outside the gateway process — identity management, distributed counters, persistent storage, and async messaging.

---

## Consequences

- Security modules are versioned and deployed together with the gateway.
- Scaling means scaling the whole gateway process (acceptable, as it is stateless).
- Individual modules cannot be independently scaled — not a concern at this stage.
- Code organization discipline is required to keep modules cohesive.
