# Sentinel Gateway — Roadmap

Each phase is an independently working increment. A phase is complete only when:
- the feature compiles and all tests pass
- the feature runs locally and is verified end-to-end
- security and concurrency reviews are complete
- documentation is updated
- the feature branch is committed and pushed

---

## Phase 1 — Bootstrap ✅
**Branch:** `feature/bootstrap`

- [x] Java 17 + Spring Boot + Maven multi-module project
- [x] Spring Cloud Gateway with single route
- [x] Hello service (smoke-test downstream)
- [x] Spring Boot Actuator health endpoints
- [x] Docker + Docker Compose configuration
- [x] `.gitignore`, `.env.example`
- [x] `README.md`, `ARCHITECTURE.md`, `ROADMAP.md`, `SECURITY_MODEL.md`

---

## Phase 2 — Routing Engine ✅
**Branch:** `feature/routing-engine`

- [x] Route model: `routeId`, `path`, `serviceUri`, `methods`, `enabled`, `requiredScopes`, `tenantRequired`, `rateLimitPolicy`
- [x] Routes for: `/api/users/**`, `/api/orders/**`, `/api/payments/**`
- [x] Test services: user-service, order-service, payment-service
- [x] Tests: valid route, unknown route, disabled route, unsupported method

---

## Phase 3 — OAuth2 / OIDC / Keycloak ✅
**Branch:** `feature/oauth2-oidc`

- [x] Keycloak in Docker Compose (realm, clients, users, roles)
- [x] Gateway as OAuth2 Resource Server
- [x] JWT validation: signature, issuer, expiry, not-before
- [x] JwtHeadersFilter: propagates X-User-Id / X-Tenant-Id to upstream
- [x] Tests: no token → 401, invalid sig → 401, expired → 401, wrong issuer → 401, nbf → 401, valid → 200

---

## Phase 4 — JWT Identity Model ✅
**Branch:** `feature/jwt-identity`

- [x] `AuthenticatedPrincipal`: userId, tenantId, roles, scopes, clientId, authenticationType
- [x] `JwtPrincipalExtractor`: Keycloak realm_access.roles + generic roles + scope/scp claims
- [x] JwtHeadersFilter updated: X-User-Id, X-Tenant-Id, X-User-Roles propagated from verified token only
- [x] Security documentation (ADR-003)

---

## Phase 5 — RBAC and Permissions ✅
**Branch:** `feature/rbac`

- [x] Roles: ADMIN, USER, SUPPORT, SERVICE
- [x] Permissions: USER_READ/WRITE, ORDER_READ/WRITE, PAYMENT_READ/WRITE, ADMIN_ALL
- [x] RolePermissions: static role → permission mapping
- [x] RouteAuthorizationFilter: enforces requiredScopes per route
- [x] 401 for unauthenticated (Spring Security); 403 for unauthorized (RBAC)
- [x] Comprehensive authorization test matrix (13 scenarios)

---

## Phase 6 — API Keys ✅
**Branch:** `feature/api-keys`

- [x] `X-API-Key` header authentication
- [x] R2DBC persistence (H2 in-memory dev/test; swap to PostgreSQL R2DBC in prod): clientId, tenantId, key hash, status, scopes, timestamps
- [x] Never store raw key; show only on creation (SHA-256 hash stored)
- [x] States: ACTIVE, REVOKED, EXPIRED
- [x] Tests: valid, revoked, expired, wrong key, missing scope → 403

---

## Phase 7 — Multi-Tenancy ✅
**Branch:** `feature/multi-tenancy`

- [x] Every authenticated request carries trusted tenant identity
- [x] Tenant context verified from authentication mechanism (not arbitrary headers)
- [x] Prevent tenant spoofing (X-Tenant-Id header spoofing rejected)
- [x] Tests: tenant present → allow; missing tenant on required route → 403; spoofed header → 401/403

---

## Phase 8 — Distributed Rate Limiting ✅
**Branch:** `feature/rate-limiting`

- [x] Redis-backed fixed-window rate limiter (Lua script — atomic INCR+EXPIRE)
- [x] Policies: ANONYMOUS (100/min), USER (1000/min), PREMIUM (10000/min)
- [x] 429 response with `X-RateLimit-Limit/Remaining/Reset` headers
- [x] Concurrent request tests; rate limiter gated on `sentinel.rate-limit.enabled`

---

## Phase 9 — Quotas ✅
**Branch:** `feature/quotas`

- [x] Tenant-level daily/monthly quotas (distinct from per-second rate limits)
- [x] Redis INCR+EXPIRE counters; config via QuotaProperties (per-tenant overrides supported)
- [x] 429 on quota exceeded with X-Quota-Limit/Used/Reset headers

---

## Phase 10 — HMAC Request Signing ✅
**Branch:** `feature/request-signing`

- [x] `X-Client-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature` headers
- [x] HMAC-SHA256 over: method + path + timestamp + nonce + body hash (constant-time verify)
- [x] Configurable timestamp validity window (default 5 min)
- [x] Redis nonce store (SET NX EX) with TTL for atomic replay prevention
- [x] Valid HMAC provides standalone HmacAuthentication (no JWT required)
- [x] Tests: valid→200, invalid sig→401, tampered body→401, expired→401, reused nonce→401

---

## Phase 11 — Security Policy Engine ✅
**Branch:** `feature/policy-engine`

- [x] Route-level policy model (JSON/DB-backed)
- [x] Policy fields: allowedMethods, requiredScopes, requireMfa, rateLimitPolicy, requestSigningRequired
- [x] Extensible evaluator (no giant if/else chains)

---

## Phase 12 — Threat Detection ✅
**Branch:** `feature/threat-detection`

- [x] Pattern detection: path traversal, SQLi, XSS, command injection
- [x] Behavioral: excessive auth failures, suspicious rates, blocked IPs
- [x] Risk scoring (0–100+) with configurable thresholds
- [x] Actions: ALLOW / LOG / BLOCK / BLOCK+ALERT

---

## Phase 13 — Audit Logging ✅
**Branch:** `feature/audit-logging`

- [x] `AuditEvent` record: requestId, timestamp, clientIp, method, path, routeId, responseStatus, outcome
- [x] `AuditEventPublisher` interface with `LoggingAuditEventPublisher` (fallback) and `KafkaAuditEventPublisher` (conditional)
- [x] `AuditLoggingFilter` as outermost WebFilter (HIGHEST_PRECEDENCE+1), lazy publish via `Mono.defer`
- [x] Kafka producer (fire-and-forget) via `KafkaAuditConfig`; gated on `sentinel.audit.kafka.enabled`
- [x] `KafkaAutoConfiguration` excluded globally to prevent connection failures when Kafka is absent
- [x] `outcomeFor()` maps status codes → semantic outcomes (ALLOWED, UNAUTHENTICATED, BLOCKED_WAF, etc.)
- [x] Tests: authenticated→ALLOWED, unauthenticated→UNAUTHENTICATED, WAF-blocked→BLOCKED_WAF, requestId propagated, exactly-one event per request

---

## Phase 14 — Observability ✅
**Branch:** `feature/observability`

- [x] `GatewayMetricsFilter` at HIGHEST_PRECEDENCE: records `gateway.requests.total` counter and `gateway.request.duration` timer per request, tagged with method/route/status/outcome
- [x] Prometheus scrape endpoint via `PrometheusEndpointConfiguration` (workaround for Spring Boot 3.3.4 `@ConditionalOnAvailableEndpoint` bug on inner `@Configuration` classes); uses per-context `CollectorRegistry` to prevent test contamination
- [x] Micrometer Tracing OTel bridge: populates MDC with `traceId`/`spanId` for log correlation (100% sampling in dev; tune in prod)
- [x] Structured JSON logging: `logback-spring.xml` with `!production` (readable pattern) and `production` (LogstashEncoder JSON) profiles
- [x] `/actuator/prometheus` endpoint exposed and accessible without authentication
- [x] Tests: prometheus accessible, requests_total incremented, request_duration recorded, route tag present, unauthenticated 401s counted with correct outcome tag

---

## Phase 15 — Service Discovery / Dynamic Backends ✅
**Branch:** `feature/service-discovery`

- [x] Added `spring-cloud-starter-loadbalancer` — enables `ReactiveLoadBalancerClientFilter` for `lb://` URI resolution
- [x] All default routes now use `lb://service-name` URIs instead of hardcoded `http://localhost:XXXX` addresses
- [x] Simple Discovery Client (`spring.cloud.discovery.client.simple.instances`) provides static instance registration for local dev/Docker Compose; env-var overrides (`USER_SERVICE_URL`, etc.) propagate to instance URIs
- [x] In Kubernetes, swap Simple Discovery Client for `spring-cloud-starter-kubernetes-client-all` to get DNS-backed service instances automatically
- [x] Tests: `lb://` URI routes correctly to registered instance, round-robin load-balances across two instances, direct `http://` URIs continue to work alongside `lb://` routes (no regression)

---

## Phase 16 — Resilience
**Branch:** `feature/resilience`

- [ ] Per-route timeouts
- [ ] Conditional retries (GET/HEAD only; never POST/payments)
- [ ] Circuit breaker (CLOSED → OPEN → HALF_OPEN → CLOSED)
- [ ] Bulkhead-style concurrency limits
- [ ] Tests: slow service, repeated failures, connection error

---

## Phase 17 — Admin API
**Branch:** `feature/admin-api`

- [ ] Protected endpoints: routes, API keys, policies, tenants, audit events, metrics
- [ ] Role-guarded (ADMIN role required)
- [ ] API key revocation

---

## Phase 18 — Admin Dashboard
**Branch:** `feature/admin-dashboard`

- [ ] Next.js + TypeScript
- [ ] Metrics overview, route management, API key management, audit log viewer
- [ ] Tenant management, policy management

---

## Phase 19 — Docker Compose (Full)
**Branch:** `feature/docker-compose`

- [ ] Complete local stack: gateway + Keycloak + PostgreSQL + Redis + Kafka + test services + Prometheus + Grafana
- [ ] `docker compose up` as primary dev startup
- [ ] Data initialization scripts

---

## Phase 20 — Kubernetes
**Branch:** `feature/kubernetes`

- [ ] Kubernetes manifests: deployments, services, config maps, secrets refs
- [ ] Health/readiness probes
- [ ] Resource requests/limits
- [ ] Multi-replica gateway

---

## Phase 21 — Helm
**Branch:** `feature/helm`

- [ ] Helm chart with configurable replicas, images, resources, ingress, autoscaling
- [ ] `helm lint` validation
- [ ] Template tests

---

## Phase 22 — CI/CD
**Branch:** `feature/cicd`

- [ ] GitHub Actions: compile → test → static analysis → dependency scan → Docker build → container scan → publish
- [ ] No secrets in source control; GitHub Secrets / OIDC

---

## Phase 23 — Security Test Suite
**Branch:** `feature/security-tests`

- [ ] Automated tests: expired/invalid/tampered JWT, missing scopes, API key reuse/revoke/expire, tenant spoofing, replay, SQLi/XSS patterns, oversized requests, unauthorized admin

---

## Phase 24 — Performance Testing
**Branch:** `feature/performance`

- [ ] k6 or Gatling benchmark suite
- [ ] Baselines: 1K, 5K, 10K req/sec
- [ ] Measure: P50/P95/P99 latency, throughput, error rate, CPU/memory
- [ ] Test scenarios: routing-only, with auth, with authz, with rate limiting, full pipeline

---

## Phase 25 — Production Hardening
**Branch:** `feature/production-hardening`

- [ ] Full system security review
- [ ] Threat model (`THREAT_MODEL.md`)
- [ ] Architecture Decision Records complete
- [ ] Graceful shutdown, liveness/readiness probes
- [ ] Failure mode documentation: Kafka down, Redis down, PostgreSQL down, downstream down
