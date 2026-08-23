# Sentinel Gateway

[![Build](https://github.com/Pulkit0103/sentinel-gateway/actions/workflows/ci.yml/badge.svg)](https://github.com/Pulkit0103/sentinel-gateway/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue.svg)](https://docs.docker.com/compose/)

**A production-grade Zero-Trust API Security and Traffic Management Platform**

Sentinel Gateway enforces a zero-trust security model where every request is authenticated, authorized, tenant-isolated, rate-limited, and audited **before** reaching any downstream service. Built on Spring Boot 3.3 and Spring Cloud Gateway (reactive/WebFlux), it delivers high throughput without sacrificing security.

---

## Table of Contents

- [What It Does](#what-it-does)
- [Architecture](#architecture)
- [Security Pipeline](#security-pipeline)
- [Feature Highlights](#feature-highlights)
- [Technology Stack](#technology-stack)
- [Quick Start](#quick-start)
- [Docker Compose (Full Stack)](#docker-compose-full-stack)
- [Admin Dashboard](#admin-dashboard)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Testing](#testing)
- [Kubernetes & Helm](#kubernetes--helm)
- [CI/CD](#cicd)
- [Roadmap](#roadmap)

---

## What It Does

Sentinel Gateway sits at the edge of your microservices cluster. Every inbound request passes through a nine-layer security pipeline before it reaches a protected service:

```
Internet → [TLS] → [Auth] → [AuthZ] → [Tenant] → [Policy] → [Threat] → [Rate Limit] → [Quota] → [Route]
```

Each layer is independently configurable. A payment API can require MFA + HMAC request signing + a lower rate limit tier, while a public read endpoint requires only authentication.

---

## Architecture

```
                         INTERNET
                            │
                            ▼
                     Load Balancer / Ingress
                            │
                            ▼
          ┌─────────────────────────────────────┐
          │          Sentinel Gateway           │
          │              :8080                  │
          │                                     │
          │  ① TLS Termination                 │
          │  ② Request ID Assignment           │
          │  ③ JWT / API-Key Authentication    │
          │  ④ RBAC Authorization              │
          │  ⑤ Tenant Isolation               │
          │  ⑥ Security Policy Evaluation     │
          │  ⑦ WAF / Threat Detection         │
          │  ⑧ Redis Rate Limiting            │
          │  ⑨ Tenant Quota Enforcement       │
          │  ⑩ HMAC Request Signing           │
          │  ⑪ Dynamic Routing (lb://)        │
          │  ⑫ Async Audit → Kafka            │
          │  ⑬ Prometheus Metrics / OTEL      │
          └────────────┬────────────────────────┘
                       │
         ┌─────────────┼──────────────────┐
         ▼             ▼                  ▼
   User Service   Order Service   Payment Service
     :8082          :8083            :8084
```

**Supporting infrastructure (Docker Compose / Kubernetes):**

| Service    | Port | Role |
|------------|------|------|
| Keycloak   | 8180 | OAuth2 / OIDC identity provider |
| PostgreSQL | 5432 | Tenant config, API keys, policies, routes |
| Redis      | 6379 | Rate-limit counters, quota counters, nonce store |
| Kafka      | 9092 | Async audit event streaming |
| Prometheus | 9090 | Metrics scraping |
| Grafana    | 3000 | Dashboards |
| Admin UI   | 3001 | Next.js management dashboard |

---

## Security Pipeline

Every request traverses this pipeline in order. A rejection at any stage returns a structured error and short-circuits further processing.

```
Request
  │
  ▼
① TLS Termination
  │  All external traffic arrives over HTTPS.
  ▼
② Request ID (RequestIdFilter)
  │  Assigns X-Request-ID and X-Correlation-ID to every request.
  ▼
③ Authentication
  │  JWT (Bearer token from Keycloak — validates sig, iss, aud, exp)
  │  API Key (X-API-Key header — bcrypt hash lookup in PostgreSQL)
  │  → 401 if missing or invalid
  ▼
④ Authorization (RBAC + Scopes)
  │  Role: ADMIN | USER | SUPPORT | SERVICE
  │  Permission: USER_READ, ORDER_WRITE, PAYMENT_READ, etc.
  │  → 403 if insufficient
  ▼
⑤ Tenant Isolation (TenantIsolationFilter)
  │  tenantId extracted from validated JWT/API key — never from headers
  │  Cross-tenant access denied unless ADMIN/SUPPORT role
  │  → 403 if tenant mismatch
  ▼
⑥ Security Policy Engine (PolicyEnforcementFilter)
  │  Per-route policies: required scopes, MFA, signing, allowed methods
  │  → 403 POLICY_VIOLATION if any check fails
  ▼
⑦ Threat Detection (ThreatDetectionFilter)
  │  WAF patterns: path traversal, SQLi, XSS, command injection
  │  Risk scoring: 0–30 allow, 31–60 log, 61+ block
  │  → 400 THREAT_DETECTED if score ≥ block threshold
  ▼
⑧ Rate Limiting (RateLimitFilter + Redis)
  │  Token bucket per (IP | userId+tenantId | clientId)
  │  Headers: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset
  │  → 429 Too Many Requests + Retry-After
  ▼
⑨ Quota Enforcement (QuotaEnforcementFilter + Redis)
  │  Tenant daily / monthly request quota
  │  → 429 QUOTA_EXCEEDED
  ▼
⑩ HMAC Signing Verification (HmacVerificationFilter)
  │  Optional per-route: validates X-Signature (HMAC-SHA256)
  │  Replay prevention via Redis nonce store (TTL = tolerance window)
  │  → 403 REPLAY_DETECTED or INVALID_SIGNATURE
  ▼
⑪ Dynamic Routing (Spring Cloud Gateway lb://)
  │  Routes defined in application.yml; DB-backed routing planned
  │  Load-balanced via SimpleDiscoveryClient (local) / K8s DNS
  ▼
Downstream Service Response
  │
  ▼
⑫ Audit Event (AuditLoggingFilter)
  │  Publishes structured AuditEvent async to Kafka
  │  Never blocks the request path
  ▼
⑬ Metrics / Traces (GatewayMetricsFilter)
     Prometheus counters + histograms, OpenTelemetry traces
```

---

## Feature Highlights

### Authentication
- **JWT / OAuth2 / OIDC** — Validates Bearer tokens against Keycloak JWKS endpoint. Checks signature, issuer, audience, and expiry.
- **API Keys** — Machine-to-machine auth via `X-API-Key`. Keys stored as bcrypt hashes; raw key shown only at creation.

### Authorization
- **RBAC** — Four roles (ADMIN, USER, SUPPORT, SERVICE) with a fine-grained permission matrix.
- **Scope enforcement** — Routes declare required OAuth2 scopes; missing scope → 403.

### Tenant Isolation
- Tenant context derived exclusively from validated credentials — never from caller-supplied headers.
- Cross-tenant requests blocked unless the caller holds ADMIN or SUPPORT with explicit permission.

### Rate Limiting
- Distributed token bucket via Redis Lua scripts (race-free).
- Three tiers: ANONYMOUS (100/min), USER (1000/min), PREMIUM (10000/min).
- Per-route policy override.

### Threat Detection (WAF)
- Pattern detectors: path traversal, SQL injection, XSS, command injection.
- Risk scorer: signals add scores; configurable log / block / alert thresholds.

### HMAC Request Signing
- Per-route opt-in. Signs `{method}\n{path}\n{timestamp}\n{nonce}\n{body-hash}` with HMAC-SHA256.
- Replay protection: nonce stored in Redis with TTL = timestamp tolerance window.

### Resilience
- **Circuit breaker** (Resilience4j): opens after 50% failure rate over last 20 calls.
- **Retry** (idempotent methods only): 2 retries for GET/HEAD; never retries POST/payment routes.
- **Timeouts**: configurable connect + response timeout per route.

### Admin API (REST)
- `GET/POST/DELETE /admin/routes` — manage routes
- `GET/POST/DELETE /admin/api-keys` — manage API keys
- `GET/POST/DELETE /admin/policies` — manage security policies

### Admin Dashboard (Next.js 14)
- Real-time gateway health status
- Route management UI
- API key management
- Policy management
- Request metrics from Prometheus

### Observability
- **Prometheus** metrics at `/actuator/prometheus`
- **OpenTelemetry** distributed traces (100% sampling by default)
- **Grafana** dashboard provisioned out of the box
- **Structured JSON** audit log via Kafka topic `sentinel.audit.requests`

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Gateway framework | Spring Boot 3.3, Spring Cloud Gateway 4.1 (reactive/WebFlux) |
| Security | Spring Security, OAuth2 Resource Server, JWT |
| Identity | Keycloak 25 (OIDC / token issuance) |
| Persistence | PostgreSQL 16 + Spring Data R2DBC |
| Cache / State | Redis 7 (rate limits, quotas, nonce store) |
| Messaging | Apache Kafka 3.7 (async audit events) |
| Resilience | Resilience4j (circuit breaker, retry) |
| Observability | Micrometer, Prometheus, Grafana, OpenTelemetry |
| Admin UI | Next.js 14, TypeScript, Tailwind CSS |
| Containers | Docker, Docker Compose |
| Orchestration | Kubernetes + Helm chart |
| CI/CD | GitHub Actions |
| Testing | JUnit 5, Mockito, Spring Boot Test |

---

## Quick Start

### Prerequisites

| Tool | Version |
|---|---|
| Java | 17+ |
| Maven | 3.9+ |
| Docker | 24+ |
| Docker Compose | v2+ |

### Option A — Local (no Docker, minimal deps)

```bash
# 1. Clone
git clone https://github.com/Pulkit0103/sentinel-gateway.git
cd sentinel-gateway

# 2. Build all modules
mvn clean package -DskipTests

# 3. Start hello-service (terminal 1)
java -jar test-services/hello-service/target/hello-service-*.jar

# 4. Start gateway (terminal 2)
java -jar gateway/target/sentinel-gateway-*.jar

# 5. Smoke test
curl http://localhost:8080/api/hello
# {"message":"Hello from Sentinel Gateway!","service":"hello-service","timestamp":"..."}

# 6. Health check
curl http://localhost:8080/actuator/health
```

> Note: Without Redis/Keycloak, rate limiting and JWT auth are disabled by default (fail-open). The gateway still routes traffic and enforces the filter chain.

### Option B — Full stack with Docker Compose

```bash
cd infrastructure/docker-compose

# Copy and review environment config
cp .env.example .env

# Build and start everything
docker compose up --build

# Tail gateway logs
docker compose logs -f sentinel-gateway
```

Services start in dependency order: PostgreSQL → Redis → Kafka → Keycloak → Gateway → Downstream services → Admin Dashboard.

---

## Docker Compose (Full Stack)

Once the stack is up, these URLs are available:

| Service | URL | Credentials |
|---|---|---|
| Gateway | http://localhost:8080 | — |
| Admin Dashboard | http://localhost:3001 | Bearer token from Keycloak |
| Keycloak | http://localhost:8180 | admin / admin |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | admin / admin |

### Get a JWT and call a protected route

```bash
# 1. Obtain a token from Keycloak
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/sentinel/protocol/openid-connect/token \
  -d 'client_id=sentinel-gateway-client' \
  -d 'grant_type=password' \
  -d 'username=alice' \
  -d 'password=alice-password' \
  | jq -r .access_token)

# 2. Call a protected endpoint through the gateway
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/users
# → routes to user-service with tenant context injected

# 3. Try the admin API
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/routes
```

### Stop the stack

```bash
docker compose down        # keep volumes
docker compose down -v     # also remove volumes (clean state)
```

---

## Admin Dashboard

The Next.js admin dashboard connects to the gateway's REST admin API.

**Dashboard** (`http://localhost:3001`):
- Gateway health indicator (UP / DOWN / DEGRADED)
- Live metric cards: total requests, active routes, active API keys, active policies

**Routes** (`/routes`):
- List all registered routes with path, methods, enabled status
- Enable / disable routes without restarting the gateway

**API Keys** (`/api-keys`):
- View all API keys (status, tenant, expiry)
- Revoke or create keys

**Policies** (`/policies`):
- View per-route security policies
- See required scopes, MFA requirement, signing requirement

> The dashboard requires a valid admin Bearer token. Paste it via the "Set Token" dialog on first load.

---

## API Reference

### Gateway endpoints (Phase 1 routes)

| Method | Path | Description |
|---|---|---|
| GET | `/api/hello` | Smoke-test route → hello-service |
| GET | `/api/users/**` | → user-service |
| GET/POST/PUT/DELETE | `/api/orders/**` | → order-service |
| GET/POST | `/api/payments/**` | → payment-service (PREMIUM rate limit) |
| GET | `/actuator/health` | Gateway health |
| GET | `/actuator/health/liveness` | Kubernetes liveness probe |
| GET | `/actuator/health/readiness` | Kubernetes readiness probe |
| GET | `/actuator/metrics` | Micrometer metrics |
| GET | `/actuator/prometheus` | Prometheus scrape endpoint |

### Admin API

| Method | Path | Description |
|---|---|---|
| GET | `/admin/routes` | List all routes |
| POST | `/admin/routes` | Create route |
| DELETE | `/admin/routes/{id}` | Delete route |
| GET | `/admin/api-keys` | List API keys |
| POST | `/admin/api-keys` | Create API key |
| DELETE | `/admin/api-keys/{id}` | Revoke API key |
| GET | `/admin/policies` | List security policies |
| POST | `/admin/policies` | Create policy |
| DELETE | `/admin/policies/{id}` | Delete policy |

### Error response format

All error responses follow a consistent structure — internal details are never exposed:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "requestId": "req-abc-123",
  "status": 403,
  "error": "FORBIDDEN",
  "code": "INSUFFICIENT_SCOPE",
  "message": "Required permission is missing"
}
```

---

## Configuration

Copy `.env.example` → `.env` and set values for your environment.

Key environment variables:

| Variable | Default | Description |
|---|---|---|
| `GATEWAY_PORT` | 8080 | Gateway listen port |
| `KEYCLOAK_JWKS_URI` | `http://localhost:8180/realms/sentinel/...` | JWKS endpoint for JWT validation |
| `KEYCLOAK_ISSUER` | `http://localhost:8180/realms/sentinel` | Expected JWT issuer |
| `R2DBC_URL` | `r2dbc:h2:mem:///sentinel` | Database URL (use PostgreSQL in prod) |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `SENTINEL_RATE_LIMIT_ENABLED` | `false` | Enable Redis-backed rate limiting |
| `SENTINEL_QUOTA_ENABLED` | `false` | Enable tenant quota enforcement |
| `SENTINEL_THREAT_DETECTION_ENABLED` | `false` | Enable WAF pattern detection |
| `SENTINEL_AUDIT_KAFKA_ENABLED` | `false` | Enable Kafka audit event publishing |
| `HMAC_SHARED_SECRET` | *(required when signing enabled)* | HMAC signing secret |

**Never commit `.env`** — only `.env.example` (with placeholder values) is tracked.

---

## Testing

```bash
# Run all tests
mvn test

# Gateway tests only
mvn test -pl gateway

# Specific test class
mvn test -pl gateway -Dtest=ThreatDetectionFilterTest

# With coverage report
mvn test jacoco:report
```

Test categories in `gateway/`:
- Unit tests — filter logic, RBAC rules, HMAC signing
- Integration tests — full Spring context with WebTestClient
- Security regression tests — OWASP-style injection probes
- Performance tests — throughput/latency baselines

---

## Kubernetes & Helm

### Deploy with raw manifests

```bash
kubectl apply -f deployment/kubernetes/
```

### Deploy with Helm

```bash
# Install
helm install sentinel-gateway deployment/helm/sentinel-gateway \
  --set gateway.image.tag=latest \
  --set keycloak.enabled=true

# Upgrade
helm upgrade sentinel-gateway deployment/helm/sentinel-gateway

# Uninstall
helm uninstall sentinel-gateway
```

The Helm chart includes:
- Deployment with liveness (`/actuator/health/liveness`) and readiness (`/actuator/health/readiness`) probes
- HorizontalPodAutoscaler
- ConfigMap for application config
- Secrets for database / Redis / Keycloak credentials
- ServiceMonitor for Prometheus Operator
- Ingress with TLS

---

## CI/CD

GitHub Actions pipelines in `.github/workflows/`:

| Workflow | Trigger | Steps |
|---|---|---|
| `ci.yml` | Push / PR to any branch | Build → Test → OWASP Dependency Check → Docker build |
| `release.yml` | Tag push (`v*`) | Full build → Docker push to GHCR → Helm package |

---

## Roadmap

All 25 phases complete as of this release:

| Phase | Feature | Branch |
|---|---|---|
| 1 | Bootstrap (Spring Cloud Gateway, hello-service) | `feature/bootstrap` |
| 2 | Multi-service routing engine | `feature/routing-engine` |
| 3 | OAuth2 / OIDC (Keycloak integration) | `feature/oauth2-oidc` |
| 4 | JWT identity extraction | `feature/jwt-identity` |
| 5 | RBAC authorization | `feature/rbac` |
| 6 | API key authentication | `feature/api-keys` |
| 7 | Multi-tenancy isolation | `feature/multi-tenancy` |
| 8 | Redis rate limiting | `feature/rate-limiting` |
| 9 | Tenant quotas | `feature/quotas` |
| 10 | HMAC request signing + replay prevention | `feature/request-signing` |
| 11 | Security policy engine | `feature/policy-engine` |
| 12 | WAF / Threat detection | `feature/threat-detection` |
| 13 | Async audit logging (Kafka) | `feature/audit-logging` |
| 14 | Observability (Prometheus, Grafana, OTEL) | `feature/observability` |
| 15 | Service discovery + dynamic routing | `feature/service-discovery` |
| 16 | Resilience (circuit breaker, retry, timeouts) | `feature/resilience` |
| 17 | Admin REST API | `feature/admin-api` |
| 18 | Next.js admin dashboard | `feature/admin-dashboard` |
| 19 | Full Docker Compose local stack | `feature/docker-compose` |
| 20 | Kubernetes manifests | `feature/kubernetes` |
| 21 | Helm chart | `feature/helm` |
| 22 | GitHub Actions CI/CD | `feature/cicd` |
| 23 | Security test suite (OWASP) | `feature/security-tests` |
| 24 | Performance test suite | `feature/performance` |
| 25 | Production hardening (threat model, ADRs, graceful shutdown) | `feature/production-hardening` |

---

## Architecture Decision Records

See `docs/adr/` for the rationale behind key design choices:

- [ADR-001](docs/adr/ADR-001-gateway-modular-monolith.md) — Modular monolith vs microservices for security controls
- [ADR-002](docs/adr/ADR-002-reactive-gateway.md) — Reactive (WebFlux) over servlet-based gateway
- [ADR-003](docs/adr/ADR-003-jwt-identity-model.md) — JWT identity extraction model
- [ADR-004](docs/adr/ADR-004-postgresql-r2dbc.md) — PostgreSQL + R2DBC for reactive persistence
- [ADR-005](docs/adr/ADR-005-redis-rate-limiting.md) — Redis token bucket for distributed rate limiting
- [ADR-006](docs/adr/ADR-006-kafka-audit-logging.md) — Kafka for async audit event streaming
- [ADR-007](docs/adr/ADR-007-filter-chain-ordering.md) — GlobalFilter chain ordering

---

## Security Notes

- **Never expose Phase 1 or development builds** to a public network without Keycloak + TLS configured.
- All secrets (DB password, Redis password, HMAC secret) are injected via environment variables — never committed.
- API key raw values are shown **once** at creation time; only bcrypt hashes are stored.
- Audit events are **immutable** — written asynchronously to Kafka; never modified after publishing.
- The threat detection engine is demonstrable WAF-style detection for portfolio purposes — not a replacement for a commercial WAF.

---

## License

MIT License — see [LICENSE](LICENSE) for details.
