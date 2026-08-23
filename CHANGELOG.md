# Changelog

All notable changes to this project will be documented in this file.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)
Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html)

---

## [0.5.0] – 2026-08-24 — Phase 5: Traffic Splitting & Canary Routing

### Added
- `CanaryConfig` (POJO): per-route canary configuration holding `canaryUri` and `weight` (0–100)
- `CanaryProperties` (`@Component`, `@ConfigurationProperties(prefix = "sentinel.canary")`): binds
  `sentinel.canary.routes.<routeId>.*` YAML into a `Map<String, CanaryConfig>` — no DB changes needed
- `CanaryRoutingFilter` (`GlobalFilter`, `Ordered.LOWEST_PRECEDENCE - 100`): for each request, looks
  up the matched route in `CanaryProperties`; draws a random int 1–100 and, when it is ≤ `weight`,
  rewrites `GATEWAY_REQUEST_URL_ATTR` to point at the canary backend (scheme + host + port replaced,
  path preserved) and tags the exchange with `X-Canary=true` for observability
- Integration tests: `CanaryRoutingFilterTest` (weight=100, all requests hit canary, 0 to stable)
  and `CanaryRoutingZeroWeightTest` (weight=0, all requests hit stable, 0 to canary) — total suite
  now 176 tests, 0 failures

### Design notes
- Canary config is runtime YAML only; reload via Spring Cloud Config or pod restart
- Order `LOWEST_PRECEDENCE - 100` ensures the filter runs after route matching sets
  `GATEWAY_ROUTE_ATTR` but before `NettyRoutingFilter` dispatches the actual HTTP call

---

## [0.4.0] – 2026-08-24 — Phase 4: Request/Response Transformation

### Added
- `RequestSanitizationFilter` (`@Order(HIGHEST_PRECEDENCE + 2)`): strips client-supplied identity
  headers (`X-User-Id`, `X-Tenant-Id`, `X-User-Roles`, `X-Auth-Type`, `X-Internal-*`) before
  `JwtHeadersFilter` runs, closing the header-injection attack surface
- `ResponseHeadersFilter` (`GlobalFilter`, `LOWEST_PRECEDENCE - 10`): removes information-leaking
  response headers (`Server`, `X-Powered-By`, `Via`) from upstream responses
- `TransformProperties` (`@ConfigurationProperties(prefix = "sentinel.transform")`): configurable
  header strip lists
- Route-level `strip_prefix` (DB column + YAML entry): `StripPrefix` filter wired per-route in
  `SentinelRouteDefinitionRepository`
- Route-level `add_request_headers` (DB column + YAML entry): `AddRequestHeader` filters wired
  per-route in `SentinelRouteDefinitionRepository`
- Integration tests: `RequestSanitizationFilterTest` (3), `ResponseHeadersFilterTest` (3),
  `RouteTransformationTest` (2) — total suite now 174 tests, 0 failures

### Fixed
- Header injection: client-supplied `X-User-Id` can no longer shadow the gateway-verified identity
  header (was possible because `JwtHeadersFilter` uses `.header()` which APPENDS, not replaces)

---

## [0.3.0] – 2026-08-24 — Phase 3: Analytics & Usage Reporting

### Added
- `AnalyticsService` interface with Redis-backed (`RedisAnalyticsService`) and no-op implementations
- `AnalyticsConfig`: conditional bean registration via `@ConditionalOnProperty` / `@ConditionalOnMissingBean`
- `AnalyticsRecord` value type captured per request (route, tenant, outcome, status, method, duration)
- `AuditLoggingFilter` now records analytics in parallel with audit events (`Mono.when`)
- REST analytics endpoints: `/analytics/summary`, `/analytics/routes`, `/analytics/tenants`,
  `/analytics/timeline`, `/analytics/security`
- Hourly Redis key bucketing for time-series data (`sentinel:analytics:{date}:{hour}`)
- Full integration test suite: `RedisAnalyticsServiceTest`, `AnalyticsControllerTest`,
  `AnalyticsIntegrationTest` (166 tests, 0 failures)
- Test infrastructure: `src/test/resources/schema.sql` (DROP+CREATE for H2 isolation),
  `src/test/resources/application.yml` (complete test overrides)

### Fixed
- Method predicate in `SentinelRouteDefinitionRepository` now emits separate `_genkey_N` args per
  HTTP method (fixes multi-method route matching, e.g. `GET,POST`)
- Added `-parameters` compiler flag to resolve `@PathVariable` names in Spring WebFlux controllers
- Resilience4j `TimeLimiter` configured to 30 s in tests (was 1 s default, causing 504 timeouts)
- H2 shared in-memory database contamination across Spring test contexts: test schema.sql
  now drops and recreates tables on each new context

### Changed
- `AuditLoggingFilter`: constructor now injects `AnalyticsService`; publishes analytics and
  audit events in parallel

---

## [0.2.0] – Phase 2: Route Persistence & Admin API

### Added
- R2DBC H2/PostgreSQL persistence for routes and security policies
- Admin REST API: CRUD for routes (`/admin/routes`) and policies (`/admin/policies`)
- `RouteService.run()` seeds YAML routes into DB on first startup; DB authoritative thereafter
- `SentinelRouteDefinitionRepository`: reads routes from DB, applies Retry + CircuitBreaker filters
- API key authentication: `ApiKeyAuthenticationWebFilter`, `ApiKeyService`, `ApiKeyRepository`
- Route-level RBAC: `RouteAuthorizationFilter` checks JWT/API-key scopes against route requirements

---

## [0.1.0] – Phase 1: Foundation

### Added
- Spring Cloud Gateway scaffold with JWT authentication (Keycloak JWKS)
- Request ID injection (`X-Request-ID`), tenant isolation header
- Structured audit logging filter (`AuditLoggingFilter`)
- Observability: Micrometer metrics, Prometheus endpoint, `GatewayMetricsFilter`
- Threat detection filter (SQL injection, XSS, path traversal, command injection)
- HMAC request signing verification
- Rate limiting, quota management
- Resilience4j circuit breaker + retry
