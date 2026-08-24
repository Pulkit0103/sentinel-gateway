# Changelog

All notable changes to this project will be documented in this file.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)
Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html)

---

## [0.11.0] – 2026-08-24 — Phase 11: Per-Route Response Caching

### Added
- `cache_ttl_seconds INT` column added to the `routes` table (NULL = no caching) in both
  `gateway/src/main/resources/schema.sql` and `gateway/src/test/resources/schema.sql`
- `RouteEntity`: new `@Column("cache_ttl_seconds") Integer cacheTtlSeconds` field with
  getter/setter; `toDomain()` and `from()` propagate the value
- `RouteDefinition`: new `cacheTtlSeconds()` accessor (nullable `Integer`); added a 15-arg
  canonical constructor; the 14-arg constructor delegates to it with `null`, preserving
  backward compatibility for all existing call sites
- `RouteDefinitionProperties.RouteEntry`: new `cacheTtlSeconds` (`Integer`) field bound from
  `sentinel.gateway.routes[N].cache-ttl-seconds`; `toRouteDefinition()` passes it through
- `RouteService.update()`: propagates `cacheTtlSeconds` on route updates
- `CacheProperties` (`@Component`, `@ConfigurationProperties("sentinel.cache")`): single
  `enabled` boolean (default `false`); controls whether the cache filter bean is instantiated
- `ResponseCacheFilter` (`GlobalFilter`, order `Ordered.HIGHEST_PRECEDENCE + 4`):
  - Registered only when `sentinel.cache.enabled=true` via `@ConditionalOnProperty`
  - Skips all non-GET requests immediately
  - Looks up the matched route from `RouteRepository`; skips caching when `cacheTtlSeconds`
    is null or ≤ 0
  - Cache key format: `sentinel:cache:{routeId}:{path}[?{query}]`
  - Cache HIT: writes the cached body directly to the response with `Content-Type:
    application/json`, HTTP 200, and `X-Cache: HIT`; upstream is not called
  - Cache MISS: wraps the response in a `ServerHttpResponseDecorator`; intercepts
    `writeWith()` on HTTP 200 responses; assembles the full body bytes, stores them in
    Redis with the route's TTL, and returns the body to the caller with `X-Cache: MISS`
  - Skips caching bodies > 1 MB to avoid Redis memory pressure
  - Cache write failures are logged at WARN level and never propagate to the caller
  - Static package-private `buildCacheKey()` method enables unit testing without Spring
- `sentinel.cache.enabled: false` added to both `application.yml` (main) and
  `application.yml` (test) so the filter is absent in all existing integration tests
- `ResponseCacheFilterTest` (10 unit tests, `@ExtendWith(MockitoExtension.class)`):
  - 8 tests verifying `buildCacheKey()` format: prefix correctness, query inclusion/
    omission, route/path/query differentiation
  - 2 tests verifying `CacheProperties` defaults and mutability
- Total test suite: 203 tests, 0 failures, 0 errors

### Design notes
- `@ConditionalOnProperty(havingValue = "true")` means the bean is entirely absent when
  caching is disabled — no Redis connection is attempted in tests or Redis-free environments
- Order `HIGHEST_PRECEDENCE + 4` places the cache filter after `RequestSanitizationFilter`
  (+2), `TracingFilter` (+3), and before `JwtRevocationFilter` (+5); cached responses
  bypass the upstream but still go through all upstream response processing
- `ServerHttpResponseDecorator.writeWith()` is used (not `ModifyResponseBodyGatewayFilterFactory`)
  for explicit byte-level control and to avoid the complex `Encoder`/`Decoder` pipeline
  that `ModifyResponseBodyGatewayFilterFactory` requires
- The `DataBufferUtils.release()` call in `assembleBytes()` ensures no memory leak even
  when the body is too large to cache

---

## [0.10.0] – 2026-08-24 — Phase 10: Correlation ID & Tracing Header Propagation

### Added
- `TracingProperties` (`@Component`, `@ConfigurationProperties("sentinel.tracing")`): two fields —
  `enabled` (default `true`) and `propagateExisting` (default `true`) — control whether the filter
  runs and whether a valid incoming `traceparent` is honoured
- `TracingFilter` (`WebFilter`, `@Order(HIGHEST_PRECEDENCE + 3)`): runs after
  `RequestSanitizationFilter` (+2) and before `JwtRevocationFilter` (+5 GlobalFilter):
  - Reads `X-Request-ID` set by the upstream `RequestIdFilter`
  - Validates incoming `traceparent` against the W3C Trace Context format
    (`00-<32hex>-<16hex>-<2hex>`); if valid and `propagateExisting=true`, preserves the
    trace-id and generates a new parent-id for this gateway hop; otherwise generates a
    fully new `traceparent`
  - Strips client-supplied `tracestate` and `baggage` to prevent header injection
  - Sets `traceparent: 00-{traceId}-{parentId}-01`, `tracestate: sentinel=1`, and
    `baggage: request-id={X-Request-ID}` on every forwarded request
- `TracingFilterTest` (4 integration tests, WireMock + RSA JWT):
  1. `noTraceparent_gatewayInjectsNew` — verifies upstream receives a valid new traceparent
  2. `validTraceparent_isForwardedWithNewParentId` — verifies trace-id preserved, parent-id replaced
  3. `invalidTraceparent_gatewayReplacesWithNew` — verifies garbage input is replaced
  4. `baggageHeader_containsRequestId` — verifies `baggage: request-id=<value>` is propagated
- Total test suite: 193 tests, 0 failures, 0 errors

### Design notes
- `TracingFilter` is a `WebFilter` (not a `GlobalFilter`) so it fires before Spring Cloud
  Gateway's routing layer and is not subject to the gateway filter ordering namespace
- ID generation uses `UUID.randomUUID()` bit-formatted as lowercase hex to satisfy the
  W3C spec (32 hex chars for trace-id, 16 hex chars for parent-id) without any additional
  dependency on OpenTelemetry or Micrometer Tracing at the filter level
- The filter is always active (no `@ConditionalOnProperty`) because `enabled=false` short-circuits
  inside `filter()`, keeping the bean present for injection while doing nothing at runtime

---

## [0.9.0] – 2026-08-24 — Phase 9: Per-Route Request Timeout

### Added
- `timeout_ms BIGINT` column added to the `routes` table (NULL = use global default) in both
  `gateway/src/main/resources/schema.sql` and `gateway/src/test/resources/schema.sql`
- `RouteEntity`: new `@Column("timeout_ms") Long timeoutMs` field with getter/setter;
  `toDomain()` and `from()` propagate the value
- `RouteDefinition`: new `timeoutMs()` accessor (nullable `Long`); added a 14-arg canonical
  constructor; the 13-arg constructor delegates to it with `null`, preserving backward compat
- `RouteDefinitionProperties.RouteEntry`: new `timeoutMs` (`Long`) field bound from
  `sentinel.gateway.routes[N].timeout-ms`; `toRouteDefinition()` passes it through
- `RouteService.update()`: propagates `timeoutMs` on route updates
- `SentinelRouteDefinitionRepository.toGatewayDefinition()`: when `entity.getTimeoutMs() != null
  && > 0`, sets route metadata `"response-timeout"` (Long millis, read by `NettyRoutingFilter`
  which converts it internally via `Duration.ofMillis()`) and `"connect-timeout"` (int millis,
  capped at 3000ms); routes without a timeout use the global `spring.cloud.gateway.httpclient.response-timeout`
- Integration test `RouteTimeoutTest` (2 tests): route `/api/slow/**` with `timeout-ms=500` and
  WireMock upstream delayed 2000ms → gateway returns 504; route `/api/fast/**` with `timeout-ms=5000`
  and instant upstream → 200 OK — total suite now 189 tests, 0 failures

### Design notes
- Per-route timeout is wired exclusively via route metadata, not a `GatewayFilter`, because
  `NettyRoutingFilter` reads `"response-timeout"` from route metadata to override the global HTTP
  client timeout for that specific upstream call
- The metadata value must be a `Long` (milliseconds), not a `java.time.Duration`; passing a
  `Duration` causes `NettyRoutingFilter.getLong()` to call `toString()` → `parseLong()` which
  throws `NumberFormatException` (e.g. `"PT0.5S"` is not a long), silently falling back to the
  global timeout — a subtle Spring Cloud Gateway API pitfall

---

## [0.8.0] – 2026-08-24 — Phase 8: Per-Route Request Size Limiting

### Added
- `max_body_bytes BIGINT` column added to the `routes` table (NULL = no limit) in both
  `gateway/src/main/resources/schema.sql` and `gateway/src/test/resources/schema.sql`
- `RouteEntity`: new `@Column("max_body_bytes") Long maxBodyBytes` field with getter/setter;
  `toDomain()` and `from()` propagate the value
- `RouteDefinition`: new `maxBodyBytes()` accessor (nullable `Long`); added a 13-arg canonical
  constructor; all shorter constructors delegate through the chain, preserving backward compat
- `RouteDefinitionProperties.RouteEntry`: new `maxBodyBytes` (`Long`) field bound from
  `sentinel.gateway.routes[N].max-body-bytes`; `toRouteDefinition()` passes it through
- `RouteService.update()`: propagates `maxBodyBytes` on route updates
- `SentinelRouteDefinitionRepository.toGatewayDefinition()`: when `entity.getMaxBodyBytes() != null
  && > 0`, adds a Spring Cloud Gateway `RequestSize` filter with `maxSize={N}B`; routes without
  the field set receive no size limit
- Integration test `RequestSizeLimitTest` (2 tests): route `/api/size/**` with `max-body-bytes=100`;
  50-byte body → 200 OK; 200-byte body → 413 Payload Too Large — total suite now 187 tests, 0 failures

### Design notes
- The `RequestSize` GatewayFilter is wired per-route at route-definition time (inside
  `SentinelRouteDefinitionRepository`), so each route gets its own independent limit
- The `DataSize` argument format used by `RequestSizeGatewayFilterFactory` is `"{N}B"` for bytes;
  larger units (`KB`, `MB`) are also accepted but byte precision is used here for correctness
- Routes without `max_body_bytes` set (NULL) receive no request-size filter, preserving the existing
  Spring Cloud Gateway default behaviour (no client-side body-size enforcement)

---

## [0.7.0] – 2026-08-24 — Phase 7: Per-Route IP Allowlist/Denylist

### Added
- `IpFilterProperties` (`@ConfigurationProperties("sentinel.ip-filter")`): global `enabled` flag
  (default `true`) for the IP filtering feature
- `IpFilterService`: stateless utility bean providing `isAllowed(clientIp, allowedCidrs, blockedCidrs)`;
  supports exact-IP and CIDR-range matching via `java.net.InetAddress`; allowlist checked first,
  denylist second, empty lists mean no restriction
- `IpFilterGlobalFilter` (`GlobalFilter`, `Ordered.HIGHEST_PRECEDENCE + 1`): runs before all other
  GlobalFilters (JwtRevocationFilter at +5, JwtHeadersFilter at +10); resolves client IP from
  `X-Forwarded-For` header (first value) falling back to the TCP remote address; looks up the
  matched route's `allowedIps`/`blockedIps` from `RouteRepository`; returns 403 Forbidden when
  blocked; uses the `thenReturn`/`defaultIfEmpty`/`flatMap` pattern to avoid Mono<Void>
  double-subscription bug
- `RouteEntity`: new `@Column("allowed_ips")` and `@Column("blocked_ips")` fields with
  `allowedIpList()` and `blockedIpList()` helper methods (comma-split + trim)
- `RouteDefinition`: new `allowedIps()` and `blockedIps()` accessors; added full-arg constructor
  alongside existing 10-arg constructor (which delegates to it with empty lists)
- `RouteDefinitionProperties.RouteEntry`: new `allowedIps` / `blockedIps` `List<String>` fields
  bound from `sentinel.gateway.routes[N].allowed-ips` / `blocked-ips`
- `RouteService.update()`: propagates `allowedIps` and `blockedIps` on route updates
- DB schema (`gateway/src/main/resources/schema.sql` and `gateway/src/test/resources/schema.sql`):
  added `allowed_ips VARCHAR(1024)` and `blocked_ips VARCHAR(1024)` columns to the `routes` table
- Integration tests: `IpFilterBlocklistTest` (2 tests: blocked IP → 403, non-blocked IP → 200) and
  `IpFilterAllowlistTest` (2 tests: IP inside allowed CIDR → 200, IP outside → 403) — total suite
  now 185 tests, 0 failures

### Design notes
- `IpFilterGlobalFilter` is a `GlobalFilter` (not `WebFilter`) because at WebFilter time the route
  has not yet been matched by `RoutePredicateHandlerMapping`; at `HIGHEST_PRECEDENCE + 1` the
  `GATEWAY_ROUTE_ATTR` exchange attribute is already populated
- Client IP resolution honours `X-Forwarded-For` for reverse-proxy deployments; operators deploying
  behind a load balancer should ensure the LB sets this header and the gateway is not directly
  internet-facing (to prevent header spoofing)
- Fail-open: if the route is not found in the DB (e.g., during a DB hiccup), the request is allowed
  through to avoid false-positive 403s during transient outages

---

## [0.6.0] – 2026-08-24 — Phase 6: JWT Revocation + Token Introspection

### Added
- `TokenRevocationService` interface: `revokeToken(jti, ttl)` and `isRevoked(jti)` contract for
  JWT blocklist management
- `RedisTokenRevocationService`: Redis-backed implementation using key format
  `sentinel:revoked:{jti}` with auto-expiring TTL equal to the token's remaining lifetime
- `NoOpTokenRevocationService`: no-op fallback that always reports tokens as not revoked;
  used when Redis is unavailable or revocation is disabled
- `TokenRevocationConfig`: conditional bean registration — Redis impl when
  `sentinel.revocation.enabled=true`, no-op fallback via `@ConditionalOnMissingBean`
- `JwtRevocationFilter` (`GlobalFilter`, `HIGHEST_PRECEDENCE + 5`): checks the incoming JWT's
  `jti` claim against the Redis blocklist on every authenticated request; tokens without a JTI
  skip the check; revoked tokens receive 401 via `ResponseStatusException`; uses
  `com.nimbusds.jwt.JWTParser` to extract JTI without re-verifying the signature
- `RevocationController` (`POST /admin/revoke`): admin endpoint to revoke a token by JTI;
  validates `expiresAt` is in the future (400 if already expired), then stores JTI in Redis with
  remaining TTL; requires `ADMIN` role (enforced by Spring Security path rules on `/admin/**`)
- `RevocationRequest` record: `{jti, expiresAt}` request body for the revoke endpoint
- `sentinel.revocation.enabled: false` YAML property — set to `true` when Redis is available
- Integration tests: `RevocationFilterTest` (2 tests: not-revoked→200, revoked→401) and
  `RevocationControllerTest` (3 tests: future expiry→204, past expiry→400, non-admin→403) —
  both use `@MockBean TokenRevocationService` to avoid requiring a live Redis instance;
  total suite now 181 tests, 0 failures

### Design notes
- `JwtRevocationFilter` is a `GlobalFilter` (not `WebFilter`) so it runs inside Spring Cloud
  Gateway's filter chain AFTER Spring Security authenticates the request and populates the
  `ReactiveSecurityContextHolder`
- JTI extraction uses `JWTParser.parse()` (Nimbus JOSE+JWT, already a transitive dependency via
  `spring-security-oauth2-jose`) — no signature re-verification, just claims parsing
- The no-op fallback keeps the gateway operational without Redis (fail-open for revocation)

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
