# Changelog

All notable changes to this project will be documented in this file.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)
Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html)

---

## [0.26.0] – 2026-08-24 — Phase 26: JWT Claim-Based Route Authorization

### Added
- **`ClaimAuthorizationProperties`** (`@Component`, `@ConfigurationProperties("sentinel.claim-authorization")`):
  - `enabled: boolean` (default `false`)
  - `routes: Map<String, List<ClaimRequirement>>` — per-route requirements list
  - `ClaimRequirement`: `claim` (JWT claim name) + `allowedValues` (list of acceptable string values)
  - All requirements for a route must match (AND logic)
  - `requirementsForRoute(routeId)` helper
- **`ClaimAuthorizationFilter`** (`GlobalFilter`, order `HIGHEST_PRECEDENCE + 6`):
  - After route matching, iterates over configured requirements for the matched route
  - For each requirement, checks `jwt.getClaim(name).toString()` against `allowedValues`
  - Missing or non-matching claim → 403 JSON: `{"status":403,"error":"JWT claim authorization failed"}`
  - Non-JWT authentication passes through unchanged
  - Correct reactor pattern: `map → defaultIfEmpty(true) → flatMap`
- **`application.yml`** (main and test): `sentinel.claim-authorization.enabled: false` block added

### Tests added
- **`ClaimAuthorizationFilterTest`** (6 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `matchingClaimValue_returns200` — JWT with `department=engineering` → 200
  - `wrongClaimValue_returns403` — JWT with `department=marketing` → 403
  - `missingRequiredClaim_returns403` — JWT with no `department` claim → 403
  - `filterDisabled_wrongClaimPasses` — `enabled=false` → 200
  - `wrongClaim_403Body_hasExpectedFields` — body has `error` and `status` fields
  - `secondAllowedValue_finance_returns200` — `department=finance` also passes

---

## [0.25.0] – 2026-08-24 — Phase 25: Response Time SLA Tracking

### Added
- **`SlaProperties`** (`@Component`, `@ConfigurationProperties("sentinel.sla")`):
  - `enabled: boolean` (default `false`)
  - `routes: Map<String, Long>` — maps routeId → target response time in milliseconds
  - `hasSla(routeId)` / `targetMs(routeId)` helpers
- **`SlaTracker`** (`@Component`):
  - Per-route in-memory statistics: `totalRequests`, `breachCount`, `totalDurationMs`, `maxDurationMs`
  - `record(routeId, durationMs, targetMs)` — increments counts, marks breach if duration > target
  - `snapshots()` — returns `Map<routeId, SlaSnapshot>` with `meanDurationMs`, `maxDurationMs`,
    `breachRatePct` (0–100)
  - `reset()` — clears all stats
- **`SlaTrackingFilter`** (`GlobalFilter`, order `LOWEST_PRECEDENCE`):
  - Measures full round-trip duration (all filters + upstream)
  - Skips routes without an SLA target; no-op when `enabled=false`
  - Logs WARN on breach: `SLA breach on route {}: {}ms > {}ms target`
- **`SlaController`** (`@RestController`, `/admin/sla`):
  - `GET  /admin/sla` — returns `{enabled, configuredRoutes, statistics}`
  - `POST /admin/sla/reset` — zeros all statistics
  - All endpoints require `ROLE_ADMIN`
- **`application.yml`** (main and test): `sentinel.sla.enabled: false` block added

### Tests added
- **`SlaTrackerTest`** (7 unit tests): verifies breach counting, mean/max computation, multi-route isolation
- **`SlaControllerTest`** (5 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `getSlaReport_admin_returns200WithExpectedFields` — GET returns all expected keys
  - `afterRequest_slaStatisticsContainRoute` — sending a request populates statistics
  - `resetSla_admin_clearsStatistics` — POST reset → statistics empty
  - `getSlaReport_userRole_returns403` — USER role → 403
  - `resetSla_userRole_returns403` — USER role → 403

---

## [0.24.0] – 2026-08-24 — Phase 24: API Key Rotation

### Added
- **`ApiKeyService.rotate(keyId)`**:
  - Generates a new raw key (`sgk_<base64url-32-bytes>`), computes SHA-256 hash
  - Updates the existing `api_keys` record with the new hash — same `id`, `clientId`,
    `tenantId`, `scopes`, `status`, `expiresAt` all preserved
  - Returns `RotatedApiKey(newRawKey, entity)` — new raw key shown exactly once
  - Old raw key is immediately invalidated (hash no longer matches)
- **`POST /admin/api-keys/{id}/rotate`** (`ApiKeyAdminController`):
  - Requires `ROLE_ADMIN`
  - Returns 200 with `{newRawKey, id, clientId, status, expiresAt}`
  - Returns 404 when `id` is not found

### Tests added
- **`ApiKeyRotationTest`** (6 integration + unit tests, `@SpringBootTest RANDOM_PORT`):
  - `rotate_existingKey_returns200WithNewRawKey` — rotate returns 200 with expected fields
  - `rotate_newRawKey_startsWithSgkPrefix` — new key has `sgk_` prefix
  - `rotate_newKeyDiffersFromOldKey` — new raw key != original raw key
  - `rotate_unknownId_returns404` — 999999 ID → 404
  - `rotate_userRole_returns403` — USER role → 403
  - `rotate_updatesKeyHashInDatabase` — DB record has new hash matching `sha256(newRawKey)`

---

## [0.23.0] – 2026-08-24 — Phase 23: Active Request Metrics

### Added
- **`RequestMetricsRegistry`** (`@Component`):
  - `inflightRequests` (gauge) — `AtomicLong` incremented on request start, decremented on completion
  - `totalCompleted` (monotonic counter) — counts all completed gateway requests
  - `perRouteCompleted` (ConcurrentHashMap per routeId) — per-route completed request counts
  - `requestStarted()`, `requestCompleted(routeId)`, `reset()` operations
  - All counters are ephemeral (lost on restart); for durable metrics use Prometheus
- **`RequestMetricsFilter`** (`WebFilter`, order `HIGHEST_PRECEDENCE`):
  - Wraps all requests to track inflight and completed counts
  - Resolves route ID from `GATEWAY_ROUTE_ATTR` after chain completes
  - Skips `/admin/**` and `/actuator/**` paths — only counts actual gateway traffic
- **`RequestMetricsController`** (`@RestController`, `/admin/request-metrics`):
  - `GET  /admin/request-metrics` — returns `{inflightRequests, totalCompleted, perRouteCompleted}`
  - `POST /admin/request-metrics/reset` — zeroes all counters (useful for baselining)
  - All endpoints require `ROLE_ADMIN`

### Tests added
- **`RequestMetricsRegistryTest`** (8 unit tests): verifies counter semantics (increment, decrement, reset, null routeId)
- **`RequestMetricsControllerTest`** (5 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `getMetrics_admin_returns200WithExpectedFields` — GET returns all expected keys
  - `afterRequest_totalCompletedIncreases` — sending a request increments totalCompleted
  - `reset_admin_clearsCounters` — POST reset → counters zeroed
  - `getMetrics_userRole_returns403` — USER role → 403
  - `reset_userRole_returns403` — USER role → 403

---

## [0.22.0] – 2026-08-24 — Phase 22: JWT Audience Validation

### Added
- **`JwtAudienceProperties`** (`@Component`, `@ConfigurationProperties("sentinel.security.jwt")`):
  - `requiredAudiences: List<String>` (default `[]`) — list of acceptable `aud` values
  - `isAudienceValidationEnabled()` helper — true when list is non-empty
  - Shares the `sentinel.security.jwt` prefix with the existing issuer setting
- **`JwtAudienceFilter`** (`WebFilter`, order `0`):
  - When `requiredAudiences` is non-empty, checks every `JwtAuthenticationToken` request
  - Validates that at least one entry in the JWT `aud` claim matches a configured audience
  - Missing `aud` claim → 401; empty/non-matching `aud` → 401
  - 401 JSON body: `{"status":401,"error":"Invalid JWT audience"}`
  - Non-JWT auth (API keys) and unauthenticated paths pass through unchanged
  - Correct reactor pattern used: `map → defaultIfEmpty(true) → flatMap` to avoid double subscription
- **`application.yml`** (main and test): `sentinel.security.jwt.required-audiences: []` added

### Tests added
- **`JwtAudiencePropertiesTest`** (5 unit tests): verifies defaults and mutator round-trips
- **`JwtAudienceFilterTest`** (6 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `audienceValidationDisabled_anyJwtPasses` — `requiredAudiences=[]` → 200
  - `jwtWithMatchingAudience_returns200` — JWT `aud` matches configured audience → 200
  - `jwtWithWrongAudience_returns401` — JWT `aud` doesn't match → 401
  - `jwtWithNoAudienceClaim_returns401` — JWT has no `aud` claim → 401
  - `jwtWithOneOfMultipleAllowedAudiences_returns200` — any matching audience → 200
  - `audienceMismatch_401Body_hasExpectedFields` — body has `error` and `status` fields

---

## [0.21.0] – 2026-08-24 — Phase 21: Circuit Breaker State Admin API

### Added
- **`CircuitBreakerStateController`** (`@RestController`, `/admin/circuit-breakers`):
  - `GET  /admin/circuit-breakers` — lists all Resilience4j circuit breakers registered in
    `CircuitBreakerRegistry` with a per-CB snapshot: `name`, `state` (CLOSED/OPEN/HALF_OPEN),
    `failureRate`, `slowCallRate`, `numberOfBufferedCalls`, `numberOfFailedCalls`,
    `numberOfSuccessfulCalls`
  - `POST /admin/circuit-breakers/{name}/reset` — forces the named CB to CLOSED state
    and returns `{name, stateBefore, stateAfter}`; returns 404 when the CB name is not registered
  - Circuit breakers are named `{routeId}-cb` and created lazily on first use; the list may be
    empty on a fresh instance with no traffic
  - All endpoints require `ROLE_ADMIN`

### Tests added
- **`CircuitBreakerStateControllerTest`** (6 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `getCircuitBreakers_admin_returns200WithList` — GET returns 200 with `circuitBreakers` + `count`
  - `getCircuitBreakers_preSeededCb_appearsInList` — pre-created CB visible with all snapshot fields
  - `getCircuitBreakers_userRole_returns403` — USER role → 403
  - `resetCircuitBreaker_openCb_transitionsToClosed` — OPEN → CLOSED, stateBefore=OPEN reported
  - `resetCircuitBreaker_unknownName_returns404` — unknown CB name → 404
  - `resetCircuitBreaker_userRole_returns403` — USER role → 403

---

## [0.20.0] – 2026-08-24 — Phase 20: Admin Route Blocking

### Added
- **`RouteBlockRegistry`** (`@Component`):
  - Thread-safe `ConcurrentHashMap`-backed in-memory set of blocked route IDs
  - `block(routeId)`, `unblock(routeId)`, `unblockAll()`, `isBlocked(routeId)`, `blockedRouteIds()` operations
  - State is ephemeral (lost on restart) — designed for emergency operator actions
- **`RouteBlockFilter`** (`GlobalFilter`, order `HIGHEST_PRECEDENCE + 3`):
  - Reads `GATEWAY_ROUTE_ATTR`; if route ID is in `RouteBlockRegistry`, returns 503 immediately
  - 503 JSON body: `{"status":503,"error":"Route temporarily blocked","routeId":"..."}`
  - Complementary to route disable (disable → 404, block → 503 with no config change)
- **`RouteBlockController`** (`@RestController`, `/admin/route-blocks`):
  - `POST   /admin/route-blocks/{routeId}` — block a route (idempotent)
  - `DELETE /admin/route-blocks/{routeId}` — unblock a specific route
  - `GET    /admin/route-blocks` — list all currently blocked route IDs + count
  - `DELETE /admin/route-blocks` — clear all blocks at once
  - All endpoints require `ROLE_ADMIN`

### Tests added
- **`RouteBlockRegistryTest`** (8 unit tests): verifies block/unblock/unblockAll/isBlocked semantics
- **`RouteBlockFilterTest`** (7 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `routeNotBlocked_returns200` — unblocked route passes through
  - `routeBlocked_returns503` — directly blocked route → 503
  - `blockViaController_thenRequest_returns503` — admin POST → subsequent request 503
  - `unblockViaController_thenRequest_returns200` — admin DELETE → subsequent request 200
  - `blockedRoute_503Body_hasExpectedFields` — body has `error`, `routeId`, `status`
  - `blockController_requiresAdminRole_returns403ForUser` — USER role → 403
  - `listBlockedRoutes_showsBlockedIds` — GET /admin/route-blocks → list with count

---

## [0.19.0] – 2026-08-24 — Phase 19: Required Header Validation

### Added
- **`RequiredHeadersProperties`** (`@Component`, `@ConfigurationProperties("sentinel.required-headers")`):
  - `enabled: boolean` (default `false`) — master switch
  - `routes: Map<String, List<String>>` — maps routeId → list of required header names
  - `requiredHeadersForRoute(routeId)` helper — returns empty list for unconfigured routes
- **`RequiredHeadersFilter`** (`GlobalFilter`, order `HIGHEST_PRECEDENCE + 5`):
  - Reads `GATEWAY_ROUTE_ATTR` to identify the matched route
  - For each configured route, checks that all required headers are present in the request
  - Missing headers → 400 Bad Request with JSON body:
    `{"status":400,"error":"Missing required header","missing":["X-Idempotency-Key"]}`
  - Lists ALL missing headers in one response; partial presence reported correctly
  - No-op when `enabled=false` or route has no configured required headers
- **`application.yml`** (main and test): `sentinel.required-headers.enabled: false` block added

### Tests added
- **`RequiredHeadersPropertiesTest`** (7 unit tests): verifies defaults and mutator round-trips
- **`RequiredHeadersFilterTest`** (6 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `allRequiredHeadersPresent_returns200` — all headers present → 200
  - `missingRequiredHeader_returns400` — headers absent → 400
  - `filterDisabled_missingHeaderStillPasses` — `enabled=false` → 200
  - `missingHeader_400Body_containsMissingArray` — only the absent header listed
  - `missingHeader_400Body_hasErrorField` — body has `error` and `status` fields
  - `allHeadersMissing_400Body_listsAllInMissingArray` — all missing headers enumerated

---

## [0.18.0] – 2026-08-24 — Phase 18: Maintenance Mode

### Added
- **`MaintenanceProperties`** (`@Component`, `@ConfigurationProperties("sentinel.maintenance")`):
  - `enabled: boolean` (default `false`) — master on/off switch toggled at runtime
  - `message: String` (default "Service temporarily unavailable for maintenance") — body message
  - `retryAfterSeconds: int` (default `60`) — value set in `Retry-After` response header
  - `bypassRoles: List<String>` (default `["ROLE_ADMIN"]`) — roles that bypass maintenance mode
- **`MaintenanceFilter`** (`WebFilter`, order `HIGHEST_PRECEDENCE + 2`):
  - When `enabled=true`, returns 503 JSON `{"status":"maintenance","message":"...","retryAfterSeconds":N}`
  - Sets `Retry-After` header on all 503 responses
  - Skips `/actuator/**` (health probes) and `/admin/**` (operational controls) — always let through
  - Checks `ReactiveSecurityContextHolder` for bypass roles; admins with `ROLE_ADMIN` pass through
  - Implemented as `WebFilter` (not GlobalFilter) so it intercepts ALL paths, including unmapped ones
- **`MaintenanceController`** (`@RestController`, `/admin/maintenance`):
  - `GET  /admin/maintenance` — returns current state `{enabled, message, retryAfterSeconds}`
  - `POST /admin/maintenance/enable` — flips `enabled=true`, returns updated state
  - `POST /admin/maintenance/disable` — flips `enabled=false`, returns updated state
  - All endpoints require `ROLE_ADMIN` (enforced by `SecurityWebFilterChain`)
  - Runtime toggle takes effect immediately; change is not persisted across restarts
- **`application.yml`** (main and test): `sentinel.maintenance.enabled: false` block added

### Tests added
- **`MaintenancePropertiesTest`** (8 unit tests): verifies all defaults and mutator round-trips
- **`MaintenanceFilterTest`** (6 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `maintenanceDisabled_requestPassesThrough` — disabled → 200
  - `maintenanceEnabled_regularUser_returns503` — enabled, regular JWT → 503
  - `maintenanceEnabled_adminRole_bypassesMaintenanceMode` — enabled, ROLE_ADMIN → 200
  - `maintenanceEnabled_503Body_containsExpectedFields` — body has `status`, `message`, `retryAfterSeconds`
  - `maintenanceEnabled_503_hasRetryAfterHeader` — `Retry-After` header present
  - `maintenanceEnabled_adminEndpoint_isStillReachable` — `/admin/maintenance` reachable during maintenance
- **`MaintenanceControllerTest`** (5 integration tests):
  - `getStatus_admin_returns200WithBody` — GET status → 200
  - `enable_admin_setsEnabledTrue` — POST enable → body shows `enabled=true`
  - `disable_admin_setsEnabledFalse` — POST disable → body shows `enabled=false`
  - `enable_unauthenticated_returns401` — no auth → 401
  - `enable_userRole_returns403` — USER role → 403

---

## [0.17.0] – 2026-08-24 — Phase 17: Graceful Shutdown & In-Flight Request Draining

### Added
- **`DrainProperties`** (`@Component`, `@ConfigurationProperties("sentinel.drain")`):
  - `enabled: boolean` (default `true`) — master switch; set to `false` to skip drain
  - `timeoutSeconds: int` (default `30`) — max seconds to wait for in-flight requests
    before the JVM is forced to exit; mirrors `spring.lifecycle.timeout-per-shutdown-phase`
- **`ShutdownController`** (`@RestController`, `/admin/shutdown`):
  - `POST /admin/shutdown` — triggers programmatic graceful shutdown
  - Returns 200 with `{"status":"shutting_down","drainTimeoutSeconds":N}`
  - Context close is deferred 200 ms on a separate thread so the HTTP response is
    flushed before the Netty event loop is torn down
  - Secured by the existing `SecurityWebFilterChain`: requires `ROLE_ADMIN`
- **`application.yml`** updated:
  - `server.shutdown: graceful` (already present — confirmed)
  - `spring.lifecycle.timeout-per-shutdown-phase: 30s` (already present — confirmed)
  - `sentinel.drain.enabled: true` and `sentinel.drain.timeout-seconds: 30` added

### Tests added
- **`DrainPropertiesTest`** (4 unit tests): verifies default `enabled=true`,
  default `timeoutSeconds=30`, and both setter/getter round-trips
- **`ShutdownControllerTest`** (2 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `shutdown_withAdminRole_returns200AndShuttingDownStatus` — ADMIN POST to
    `/admin/shutdown` → 200, body contains `status=shutting_down` and `drainTimeoutSeconds=30`
  - `shutdown_withoutAuth_returns401` — no auth → 401 Unauthorized
  - `ConfigurableApplicationContext` is `@MockBean` so `close()` is a no-op in tests

---

## [0.16.0] – 2026-08-24 — Phase 16: Dynamic Route Reload

### Added
- **`RouteRefreshController`** (`@RestController`, `/admin/routes/refresh`):
  - `POST /admin/routes/refresh` — manually triggers a `RefreshRoutesEvent`, causing
    `CachingRouteLocator` to invalidate its cache and re-fetch routes from
    `SentinelRouteDefinitionRepository` without a restart
  - Returns 204 No Content
  - Protected by existing `SecurityWebFilterChain` ADMIN role requirement for `/admin/**`
- **`RouteService`** already injects `ApplicationEventPublisher` and publishes
  `RefreshRoutesEvent` after every mutating operation (create, update, delete, setEnabled)
  via the private `refresh()` method — confirmed present and wired correctly

### Tests added
- **`DynamicRouteReloadTest`** (5 integration tests, `@SpringBootTest RANDOM_PORT`, `@RecordApplicationEvents`):
  - `manualRefresh_returns204` — admin POST to `/admin/routes/refresh` → 204 No Content
  - `manualRefresh_nonAdmin_returns403` — USER role → 403 Forbidden
  - `manualRefresh_unauthenticated_returns401` — no auth → 401 Unauthorized
  - `routeService_publishesRefreshEvent_onSave` — direct `RouteService.create()` call →
    `ApplicationEvents` stream confirms at least one `RefreshRoutesEvent` was published
  - `manualRefresh_isIdempotent` — two consecutive POST /admin/routes/refresh both return 204

---

## [0.15.0] – 2026-08-24 — Phase 15: Configurable JWT Claims Forwarding + Scope Denial Audit Events

### Added
- **`JwtClaimsForwardingProperties`** (`@Component`, `@ConfigurationProperties("sentinel.jwt.claims-forwarding")`):
  - `enabled: boolean` (default `false`) — opt-in master switch
  - `mappings: List<ClaimMapping>` — each entry has `claim: String` (JWT claim name) and
    `header: String` (HTTP header name to forward to upstream)
  - Only string-valued claims are forwarded; non-string values (arrays, objects, numbers) are
    silently skipped
- **`JwtHeadersFilter`** enhanced: when `sentinel.jwt.claims-forwarding.enabled=true`, iterates
  over `mappings` and appends each configured claim as an extra upstream header immediately after
  the standard `X-User-Id` / `X-Tenant-Id` / `X-User-Roles` headers
- **`RouteAuthorizationFilter`** enhanced: on scope-mismatch 403, emits a structured audit log
  line via a dedicated `AUDIT_SECURITY` logger:
  `requestId={} path={} routeId={} outcome=FORBIDDEN_SCOPE reason="Required scopes: {} not satisfied by: {}"`
- `sentinel.jwt.claims-forwarding.enabled: false` added to `application.yml` (main and test)

### Tests added
- **`JwtClaimsForwardingTest`** (2 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `emailClaim_forwardedAsXUserEmailHeader` — JWT with `email` claim + mapping enabled →
    upstream receives `X-User-Email: test@example.com`
  - `standardHeadersStillPresent_whenClaimsForwardingEnabled` — verifies `X-User-Id` and
    `X-User-Email` are both present when claims forwarding is active
- **`JwtClaimsForwardingPropertiesTest`** (4 unit tests):
  - `defaults_enabledIsFalse` — fresh instance has `enabled=false`
  - `defaults_mappingsIsEmptyList` — fresh instance has non-null empty mappings list
  - `setEnabled_updatesFlag` — mutator round-trip
  - `setMappings_storesMappings` — stores claim/header mapping and reads back correctly

---

## [0.14.0] – 2026-08-24 — Phase 14: Tenant-Scoped Rate Limiting + Policy Admin API

### Added
- **`RateLimitPolicyController`** (`@RestController`, `/admin/rate-limit`):
  - `GET /admin/rate-limit/policies` — returns the active `sentinel.rate-limit.policies` map as JSON,
    allowing operators to inspect current rate limit tiers (ANONYMOUS, USER, PREMIUM, DEFAULT)
    without restarting or reading config files directly
  - Protected by existing `SecurityWebFilterChain` ADMIN role requirement for `/admin/**`
- **`TenantRateLimitProperties`** (`@Component`, `@ConfigurationProperties("sentinel.tenant-rate-limit")`):
  - `enabled: boolean` (default `false`) — master switch for per-tenant overrides
  - `tenants: Map<String, TenantPolicy>` — maps tenant ID (from `X-Tenant-Id` header) to a
    `TenantPolicy` with `requestsPerMinute: int` (default 1000)
  - Placeholder for future filter integration; available for injection wherever tenant-specific
    limits are needed
  - Document tenant override config via `sentinel.tenant-rate-limit.tenants.<tenantId>.requests-per-minute`
- `sentinel.tenant-rate-limit.enabled: false` block added to `application.yml` (main) with
  commented-out example tenant entries

### Tests added
- **`RateLimitPolicyControllerTest`** (2 tests):
  - `getPolicies_returnsConfiguredPolicies` — admin JWT → 200, body contains `ANONYMOUS` and `DEFAULT`
    keys with correct `requestsPerMinute` values from test `application.yml`
  - `getPolicies_requiresAuthentication_returns401` — no auth → 401
- **`TenantRateLimitPropertiesTest`** (4 tests):
  - `defaults_enabledIsFalse` — fresh instance has `enabled=false`
  - `defaults_tenantsMapIsEmpty` — fresh instance has empty (non-null) tenants map
  - `setEnabled_updatesFlag` — mutator round-trip
  - `setTenants_storesTenantPolicies` — stores tenant policy and reads back correctly
  - `tenantPolicy_defaultRequestsPerMinute_is1000` — `TenantPolicy` defaults to 1000 RPM

---

## [0.13.0] – 2026-08-24 — Phase 13: Admin Dashboard — Gateway Health Aggregation

### Added
- **`RouteHealthReport`** (record): per-route snapshot — `routeId`, `path`, `serviceUri`, `methods`,
  `enabled`, `rateLimitPolicy`, `cacheTtlSeconds`, `timeoutMs`, `maxBodyBytes`,
  `ipFilterEnabled`, `canaryEnabled`, `canaryWeight`
- **`HealthSummary`** (record): aggregated counts — `totalRoutes`, `enabledRoutes`, `disabledRoutes`,
  `routesWithCanary`, `routesWithCache`, `routesWithIpFilter`, `routesWithTimeout`
- **`GatewayHealthController`** (`@RestController`, `/admin/health`):
  - `GET /admin/health/routes` — streams all routes from `RouteRepository`, maps each
    `RouteEntity` to `RouteHealthReport`; checks `CanaryProperties.getRoutes()` for canary state
  - `GET /admin/health/summary` — collects all route reports and aggregates into `HealthSummary`
  - Protected by existing `SecurityWebFilterChain` ADMIN role requirement for `/admin/**`

### Tests added
- **`GatewayHealthControllerTest`** (4 tests):
  - `getRoutes_returnsListWithSeededRoutes` — admin JWT → 200, non-empty list with all required fields
  - `getSummary_returnsAggregatedCounts` — admin JWT → 200, `totalRoutes >= 1`, enabled+disabled=total
  - `getRoutes_unauthenticated_returns401` — no auth → 401
  - `getRoutes_nonAdminRole_returns403` — USER role → 403

---

## [0.12.0] – 2026-08-24 — Phase 12: Webhook Event Emission

### Added
- **`WebhookProperties`** (`@Component`, `@ConfigurationProperties("sentinel.webhook")`):
  - `enabled: boolean` (default `false`) — master switch for webhook delivery
  - `endpoints: List<WebhookEndpoint>` — list of target URLs with per-endpoint `secret` and
    `events` filter (empty list = accept all event types)
  - `timeoutSeconds: int` (default `5`) — HTTP request timeout per POST
  - `retryAttempts: int` (default `2`) — retry count on transient failure
- **`WebhookEvent`** (Java record): `eventType`, `requestId`, `clientIp`, `path`, `routeId`,
  `timestamp`, `metadata` — immutable carrier for all emitted gateway events
- **`WebhookService`** (interface): `Mono<Void> emit(WebhookEvent)` — fire-and-forget contract
- **`NoOpWebhookService`**: implements `WebhookService`; `emit()` returns `Mono.empty()` immediately
- **`WebhookServiceImpl`**: real implementation
  - Filters endpoints by their `events` list before delivery
  - Serializes event to JSON via Jackson `ObjectMapper`
  - Signs JSON body with HMAC-SHA256 using the endpoint secret → `X-Webhook-Signature: sha256={hex}`
  - POSTs to endpoint URL via `WebClient` with `Content-Type: application/json`
  - Retries up to `retryAttempts` times on failure using `.retry()`
  - Delivery runs on `Schedulers.boundedElastic()` — never blocks the gateway request pipeline
- **`WebhookConfig`** (`@Configuration`):
  - `@Bean WebClient webhookWebClient(WebhookProperties)` — dedicated client with response timeout
  - `@Bean @ConditionalOnProperty(havingValue = "true") WebhookService webhookService(...)` — real impl
  - `@Bean @ConditionalOnMissingBean WebhookService noOpWebhookService()` — fallback no-op
- **`IpFilterGlobalFilter`** — emits `ROUTE_BLOCKED` event (fire-and-forget) when a request is
  rejected by the IP allowlist/denylist check; includes `requestId`, `clientIp`, `path`, `routeId`
- **`JwtRevocationFilter`** — emits `TOKEN_REVOKED` event (fire-and-forget) when a revoked JTI is
  detected; includes `requestId`, `clientIp`, `path`, and `jti` in metadata
- `sentinel.webhook.enabled: false` added to both `application.yml` (main) and `application.yml`
  (test) — webhooks are absent in all existing integration tests and Redis-free environments

### Tests added
- **`WebhookServiceTest`** (5 unit tests, `@ExtendWith(MockitoExtension.class)`):
  - `disabled_emitReturnsEmpty` — `NoOpWebhookService.emit()` completes empty
  - `enabled_noMatchingEvents_skips` — endpoint `events` filtering verified; NoOp also completes empty
  - `hmacSignature_isCorrect` — expected HMAC-SHA256 signature compared against `sign()` output
  - `webhookProperties_defaultsAreCorrect` — verifies all property defaults
  - `webhookEndpoint_defaultsAreCorrect` — verifies endpoint inner-class defaults
- **`WebhookIntegrationTest`** (1 integration test, `@SpringBootTest`):
  - `contextLoads` — full application context starts with `@MockBean WebhookService`; confirms wiring

### Design notes
- Fire-and-forget is achieved by calling `.subscribe()` on the `Mono` returned by `emit()` inside
  the filter; the filter's reactive chain is never blocked waiting for webhook delivery
- HMAC signing uses `javax.crypto.Mac` with `HmacSHA256`; `HexFormat.of().formatHex()` (JDK 17)
  produces the lowercase hex digest prefixed with `sha256=`
- `@ConditionalOnProperty(havingValue = "true")` / `@ConditionalOnMissingBean` pattern mirrors the
  existing `TokenRevocationConfig` — no bean is registered when webhooks are disabled
- Total test suite: 208 tests, 0 failures, 0 errors

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
