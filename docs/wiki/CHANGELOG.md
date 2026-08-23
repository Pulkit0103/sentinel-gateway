# Sentinel Gateway — Changelog

All notable changes are documented here, organized by version. Entries follow
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/) conventions.

---

## [0.2.0] — Phase 2: Dynamic Admin CRUD + Route Persistence

### Added

#### Route Management API (Full CRUD)
- `GET /admin/routes` — list all routes (enabled and disabled)
- `GET /admin/routes/{routeId}` — retrieve a single route
- `POST /admin/routes` — create a new route; takes effect immediately (no restart)
- `PUT /admin/routes/{routeId}` — update path, serviceUri, methods, scopes, rate-limit tier
- `DELETE /admin/routes/{routeId}` — permanently remove a route; takes effect immediately
- `POST /admin/routes/{routeId}/enable` — re-enable a disabled route
- `POST /admin/routes/{routeId}/disable` — pause a route without deleting it

Routes are now persisted in the `routes` table (PostgreSQL in production, H2 in dev/test).
On startup, if the table is empty, routes are seeded from YAML for zero-migration adoption.
Every mutating operation updates the in-memory `RouteRegistry` and publishes a
`RefreshRoutesEvent` so Spring Cloud Gateway picks up the change within milliseconds.

#### Policy Management API (Full CRUD)
- `GET /admin/policies` — list all security policies
- `GET /admin/policies/{policyId}` — retrieve a single policy
- `GET /admin/policies/route/{routeId}` — get the policy attached to a route
- `POST /admin/policies` — create a new policy for a route
- `PUT /admin/policies/{policyId}` — update MFA requirement, signing, scopes, methods, tier
- `DELETE /admin/policies/{policyId}` — remove a policy (route reverts to no policy enforcement)

#### IP Blocklist Management API
- `GET /admin/blocklist` — list all blocked IPs (YAML-static + Redis-persisted)
- `POST /admin/blocklist` — block an IP address; persists to Redis, effective immediately
- `DELETE /admin/blocklist/{ip}` — unblock an IP from the runtime list

Blocked IPs are stored in Redis (`sentinel:blocklist` set) for durability across restarts
and for consistency across clustered gateway instances. The in-memory list in
`ThreatDetectionProperties` is kept in sync so the WAF filter needs no Redis round-trip
per request.

#### Infrastructure
- `RouteEntity` / `RouteRepository` — R2DBC persistence for route definitions
- `RouteService` — route lifecycle: seed-from-YAML, CRUD, hot-reload via `RefreshRoutesEvent`
- `SentinelRouteDefinitionRepository` — Spring Cloud Gateway `RouteDefinitionRepository` backed
  by the database; applies resilience filters (CircuitBreaker, Retry) dynamically
- `BlocklistService` — Redis-backed IP blocklist; syncs to `ThreatDetectionProperties` on startup
- `schema.sql` updated with `routes` table (idempotent `CREATE TABLE IF NOT EXISTS`)

### Changed
- `RouteRegistry` — changed from immutable `Map` to `ConcurrentHashMap`; now supports
  `register()`, `deregister()`, `loadAll()` for runtime mutations
- `GatewayRoutingConfig` — static `RouteLocator` bean removed; routes now served exclusively
  by `SentinelRouteDefinitionRepository` (database-backed)
- `RouteAdminController` — extended from GET-only to full CRUD with enable/disable actions
- `PolicyAdminController` — extended from GET-only to full CRUD
- `application.yml` — version bumped to `0.2.0-SNAPSHOT`, phase updated

### Tests
- `AdminApiTest` — extended to cover Phase 2: route CRUD, conflict detection, policy CRUD,
  blocklist add/remove, and validation (invalid IP format → 400)

---

## [0.1.0] — Phase 1: Foundation (25-Phase Roadmap Complete)

### Summary

Complete implementation of the 25-phase zero-trust API gateway roadmap:

| Phase | Feature | Status |
|-------|---------|--------|
| 1 | Bootstrap + project structure | ✅ |
| 2 | Routing engine (Spring Cloud Gateway) | ✅ |
| 3 | OAuth2/OIDC (Keycloak) | ✅ |
| 4 | JWT identity propagation | ✅ |
| 5 | RBAC (role→permission matrix) | ✅ |
| 6 | API key authentication | ✅ |
| 7 | Multi-tenancy isolation | ✅ |
| 8 | Redis token-bucket rate limiting | ✅ |
| 9 | Tenant quotas (daily/monthly) | ✅ |
| 10 | HMAC-SHA256 request signing + replay prevention | ✅ |
| 11 | Pluggable security policy engine | ✅ |
| 12 | WAF-style threat detection (4 pattern detectors) | ✅ |
| 13 | Async audit logging (Kafka + log fallback) | ✅ |
| 14 | Prometheus metrics + OpenTelemetry tracing | ✅ |
| 15 | Service discovery (SimpleDiscovery / K8s DNS) | ✅ |
| 16 | Resilience4j circuit breaker + retry | ✅ |
| 17 | Admin REST API (GET endpoints) | ✅ |
| 18 | Next.js 14 admin dashboard | ✅ |
| 19 | Docker Compose full stack (9 services) | ✅ |
| 20 | Kubernetes raw manifests | ✅ |
| 21 | Helm chart | ✅ |
| 22 | GitHub Actions CI/CD pipeline | ✅ |
| 23 | OWASP security regression tests | ✅ |
| 24 | Performance baseline tests | ✅ |
| 25 | Production hardening (graceful shutdown, ADRs, threat model) | ✅ |

### Key Statistics (v0.1.0)
- 68 production Java classes across 14 packages
- 23 integration/unit test classes
- 7 Architecture Decision Records (ADRs)
- Full Docker stack (9 services) with health checks
- GitHub Actions CI: compile → test → static analysis → dependency scan → Docker build → container scan
