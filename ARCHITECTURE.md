# Sentinel Gateway — Architecture

## Design Philosophy

Sentinel Gateway is a **single deployable unit** that contains all security and traffic-management logic internally. It does **not** decompose security concerns into separate microservices. Each added network hop for authentication, authorization, or rate-limiting would increase latency and introduce new failure points.

Instead, the gateway enforces the full zero-trust pipeline within a single Spring Cloud Gateway process, using clean package boundaries to keep responsibilities separated.

---

## Zero-Trust Request Pipeline

Every request passes through this pipeline. Security controls run before any request reaches a downstream service.

```
Client
  │
  ▼
TLS Termination
  │
  ▼
Request ID / Correlation ID assignment
  │
  ▼
Authentication
  │  ○ JWT (OAuth2/OIDC via Keycloak)
  │  ○ API Key
  │  ○ mTLS (future)
  ▼
Identity Extraction
  │  → AuthenticatedPrincipal (userId, tenantId, roles, scopes, clientId)
  ▼
Authorization (RBAC + Scopes)
  │
  ▼
Tenant Isolation
  │  → Verified tenant context
  ▼
Security Policy Evaluation
  │  → Route-level policies (required scopes, signing, MFA, etc.)
  ▼
Threat Detection
  │  → Risk scoring, WAF patterns, anomaly detection
  ▼
Rate Limiting (distributed via Redis)
  │
  ▼
Quota Validation (tenant-level, daily/monthly)
  │
  ▼
Request Signing Validation (HMAC-SHA256, replay protection)
  │
  ▼
Dynamic Routing
  │
  ▼
Downstream Service
  │
  ▼
Audit Event (async → Kafka)
  │
  ▼
Observability (OpenTelemetry traces, Prometheus metrics)
```

---

## Module Structure

```
sentinel-gateway/
│
├── gateway/                          ← Spring Cloud Gateway application
│   └── src/main/java/com/sentinelgateway/gateway/
│       ├── config/                   ← Route and bean configuration
│       ├── filter/                   ← Global and route-specific filters
│       ├── auth/                     ← Authentication (JWT, API Key)  [Phase 3+]
│       ├── authz/                    ← Authorization and RBAC          [Phase 5+]
│       ├── tenant/                   ← Tenant isolation                [Phase 7+]
│       ├── ratelimit/                ← Distributed rate limiting       [Phase 8+]
│       ├── quota/                    ← Tenant quotas                   [Phase 9+]
│       ├── signing/                  ← HMAC request signing            [Phase 10+]
│       ├── policy/                   ← Security policy engine          [Phase 11+]
│       ├── threat/                   ← Threat detection                [Phase 12+]
│       ├── audit/                    ← Async audit event publishing    [Phase 13+]
│       ├── routing/                  ← Dynamic route management        [Phase 15+]
│       ├── resilience/               ← Circuit breaker, retry          [Phase 16+]
│       ├── admin/                    ← Admin REST API                  [Phase 17+]
│       └── observability/            ← Metrics, tracing                [Phase 14+]
│
├── test-services/
│   ├── hello-service/                ← Phase 1 smoke-test service
│   ├── user-service/                 ← Phase 2+
│   ├── order-service/                ← Phase 2+
│   └── payment-service/              ← Phase 2+
│
├── admin-ui/                         ← Next.js admin dashboard        [Phase 18+]
│
├── infrastructure/
│   ├── docker-compose/               ← Local environment
│   ├── keycloak/                     ← Realm export / bootstrap        [Phase 3+]
│   ├── postgres/                     ← Schema migrations               [Phase 6+]
│   ├── redis/                        ← Configuration                   [Phase 8+]
│   └── kafka/                        ← Topics configuration            [Phase 13+]
│
├── deployment/
│   ├── kubernetes/                   ← Raw K8s manifests               [Phase 20+]
│   └── helm/                         ← Helm chart                      [Phase 21+]
│
├── docs/
│   ├── architecture/
│   ├── adr/                          ← Architecture Decision Records
│   ├── security/
│   └── api/
│
└── .github/
    └── workflows/                    ← CI/CD pipelines                 [Phase 22+]
```

---

## Component Responsibilities

### Spring Cloud Gateway (Reactive)

The gateway uses Spring Cloud Gateway's reactive (WebFlux-based) engine. It processes requests on a non-blocking event loop, which enables high throughput without needing one thread per connection.

Route matching and filter chain execution happen in a `ServerWebExchange`. Each security concern is a `GlobalFilter` or a route-level `GatewayFilter`.

### Keycloak (Phase 3+)

Keycloak serves as the external identity provider. The gateway does **not** manage user identities, passwords, or issue tokens — it only validates them.

Responsibilities: OAuth2 Authorization Server, OIDC discovery, token issuance, user management.

### Redis (Phase 8+)

Redis stores short-lived, frequently updated state:
- Rate-limit counters (token bucket state)
- Quota counters (daily/monthly usage)
- Used nonces for replay attack prevention
- Fast API key lookup cache

### Kafka (Phase 13+)

Audit and security events are published asynchronously to Kafka topics. The gateway does not block request processing waiting for audit writes.

Topics (indicative):
- `sentinel.audit.requests`
- `sentinel.security.events`
- `sentinel.threat.alerts`

### PostgreSQL (Phase 6+)

Persistent, query-able storage for:
- Tenant configuration
- API clients and key hashes
- Security policies
- Route definitions
- Persistent audit metadata

---

## Key Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Gateway style | Reactive (WebFlux) | Non-blocking I/O for high throughput |
| Identity provider | Keycloak | Avoid implementing OAuth2 server; Keycloak is battle-tested |
| Rate limiting storage | Redis | Distributed, atomic, fast; Lua scripts for race-free counters |
| Audit pipeline | Kafka | Decouple audit writes from request path; avoid latency |
| Module separation | Internal packages | Avoid microservice overhead for security controls |
| Retry safety | Method-aware | Only retry idempotent methods (GET, HEAD); never POST/payment |

See `docs/adr/` for full Architecture Decision Records.

---

## Infrastructure Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    Sentinel Gateway (:8080)                   │
│                                                              │
│  GlobalFilter chain:                                         │
│  RequestIdFilter → AuthFilter → AuthzFilter → TenantFilter   │
│  → PolicyFilter → ThreatFilter → RateLimitFilter             │
│  → QuotaFilter → SigningFilter → RoutingFilter               │
└──────────────────────┬───────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────────┐
        ▼              ▼                  ▼
  ┌──────────┐  ┌──────────┐  ┌──────────────────┐
  │ Keycloak │  │  Redis   │  │   PostgreSQL     │
  │  :8180   │  │  :6379   │  │     :5432        │
  └──────────┘  └──────────┘  └──────────────────┘
                                       │
                               ┌───────┴────────┐
                               ▼                ▼
                          ┌─────────┐    ┌──────────┐
                          │  Kafka  │    │ Grafana  │
                          │  :9092  │    │  :3000   │
                          └─────────┘    └──────────┘
```
