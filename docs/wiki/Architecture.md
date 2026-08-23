# Architecture

## Design Philosophy

Sentinel Gateway is a **single deployable unit** that enforces the complete zero-trust security pipeline internally. Security concerns (auth, authz, rate limiting, threat detection) are kept as **internal packages**, not separate microservices. This avoids extra network hops, reduces latency, and eliminates inter-service failure modes for security-critical paths.

The gateway uses Spring Cloud Gateway's **reactive (WebFlux) engine**, processing requests on a non-blocking event loop. This enables high throughput without one thread per connection.

## Module Structure

```
sentinel-gateway/
│
├── gateway/                              ← Spring Cloud Gateway application
│   └── src/main/java/com/sentinelgateway/gateway/
│       ├── config/                       ← Route config, bean wiring
│       ├── filter/                       ← RequestIdFilter (global)
│       ├── security/                     ← JWT extraction, RBAC, tenant isolation
│       ├── apikey/                       ← API key authentication
│       ├── ratelimit/                    ← Redis token bucket rate limiter
│       ├── quota/                        ← Tenant daily/monthly quotas
│       ├── signing/                      ← HMAC-SHA256 request signing
│       ├── policy/                       ← Security policy engine
│       ├── threat/                       ← WAF / threat detection
│       ├── audit/                        ← Kafka async audit events
│       ├── metrics/                      ← Prometheus + OpenTelemetry
│       ├── routing/                      ← Route definitions and registry
│       ├── resilience/                   ← Circuit breaker + retry config
│       └── admin/                        ← Admin REST API controllers
│
├── test-services/
│   ├── hello-service/                    ← Phase 1 smoke-test downstream
│   ├── user-service/                     ← Users domain stub
│   ├── order-service/                    ← Orders domain stub
│   └── payment-service/                  ← Payments domain stub
│
├── admin-dashboard/                      ← Next.js 14 TypeScript SPA
│   └── src/
│       ├── app/                          ← Next.js App Router pages
│       ├── components/                   ← NavBar, TokenSetup, StatusBadge
│       └── lib/                          ← API client, type definitions
│
├── infrastructure/
│   ├── docker-compose/                   ← docker-compose.yml + .env.example
│   ├── keycloak/                         ← realm-export.json (pre-configured realm)
│   ├── postgres/                         ← init.sql schema
│   ├── prometheus/                       ← prometheus.yml scrape config
│   └── grafana/                          ← Dashboard + datasource provisioning
│
├── deployment/
│   ├── kubernetes/                       ← Raw K8s manifests
│   └── helm/                             ← Helm chart
│
├── docs/
│   ├── adr/                              ← Architecture Decision Records
│   └── wiki/                             ← Wiki source markdown
│
└── .github/
    └── workflows/                        ← ci.yml, release.yml
```

## GlobalFilter Chain Ordering

Spring Cloud Gateway executes filters in order by priority. The Sentinel filter chain ordering (lower number = runs first):

| Order | Filter | Purpose |
|---|---|---|
| -10 | `RequestIdFilter` | Assigns X-Request-ID, X-Correlation-ID |
| 10 | `ApiKeyAuthenticationWebFilter` | Validates X-API-Key header |
| 20 | `JwtHeadersFilter` | Extracts principal from JWT |
| 30 | `RouteAuthorizationFilter` | RBAC: checks role + scopes |
| 40 | `TenantIsolationFilter` | Enforces tenant context |
| 50 | `PolicyEnforcementFilter` | Per-route policy checks |
| 60 | `ThreatDetectionFilter` | WAF pattern detection |
| 70 | `RateLimitFilter` | Redis token bucket |
| 80 | `QuotaEnforcementFilter` | Tenant quota check |
| 90 | `HmacVerificationFilter` | Optional HMAC signature |
| 100 | `AuditLoggingFilter` | Async Kafka audit event |
| 110 | `GatewayMetricsFilter` | Prometheus / OTEL metrics |

## Infrastructure Diagram

```
┌─────────────────────────────────────────────────────────┐
│                Sentinel Gateway (:8080)                  │
│                                                         │
│  RequestIdFilter → JwtHeaders/ApiKey → RouteAuthz       │
│  → TenantIsolation → PolicyEnforcement → ThreatDetect   │
│  → RateLimit → QuotaEnforcement → HmacVerify            │
│  → Route (lb://) → [AuditLogging] → [Metrics]           │
└──────────────────┬──────────────────────────────────────┘
                   │
      ┌────────────┼────────────────────┐
      ▼            ▼                   ▼
 ┌─────────┐  ┌─────────┐  ┌──────────────────┐
 │ Keycloak│  │  Redis  │  │   PostgreSQL     │
 │  :8180  │  │  :6379  │  │     :5432        │
 └─────────┘  └─────────┘  └──────────────────┘
                                    │
                            ┌───────┴────────┐
                            ▼                ▼
                       ┌─────────┐    ┌──────────┐
                       │  Kafka  │    │ Grafana  │
                       │  :9092  │    │  :3000   │
                       └─────────┘    └──────────┘
```

## Dynamic Routing (Phase 2)

Routes are now persisted in the `routes` table and managed entirely at runtime:

```
Admin API (POST /admin/routes)
        │
        ▼
  RouteService.create()
        │
        ├──► RouteRepository.save()       ← persists to DB
        ├──► RouteRegistry.register()     ← updates in-memory map (ConcurrentHashMap)
        └──► RefreshRoutesEvent           ← Spring Cloud Gateway re-reads routes
                    │
                    ▼
     SentinelRouteDefinitionRepository.getRouteDefinitions()
                    │
                    ▼
         Spring Cloud Gateway route table updated
```

**Startup sequence:**
1. `RouteService` checks if `routes` table is empty
2. If empty: seeds from YAML (`sentinel.gateway.routes[*]`) — zero-migration adoption
3. Loads all DB rows into `RouteRegistry` (in-memory)
4. `SentinelRouteDefinitionRepository` serves enabled routes to Spring Cloud Gateway
5. Resilience filters (CircuitBreaker, Retry) are applied per-route in the repository

## Key Design Decisions

See [Architecture Decision Records](../adr/) for full rationale.

| Decision | Choice | Reason |
|---|---|---|
| Deployment style | Modular monolith | No extra network hops for security enforcement |
| Runtime model | Reactive (WebFlux) | Non-blocking I/O; high throughput on few threads |
| Identity provider | Keycloak | Battle-tested OAuth2 / OIDC; avoid custom token issuance |
| Rate-limit storage | Redis Lua | Distributed, atomic, race-free counter operations |
| Audit pipeline | Kafka | Decouple audit writes; never block request path |
| DB driver | R2DBC | Non-blocking reactive DB access (fits WebFlux model) |
| Route storage (Phase 2) | PostgreSQL + RouteDefinitionRepository | Hot-reload without restart; Admin API managed |
| Blocklist storage (Phase 2) | Redis Set | Survives restart; shared across clustered instances |
| Retry safety | Idempotent methods only | Never retry POST or payment routes |
