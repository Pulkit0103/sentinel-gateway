# Changelog

All notable changes to this project will be documented in this file.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)
Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html)

---

## [0.51.0] – 2026-08-24 — Phase 51: Response Header Audit

### Added
- **`HeaderAuditProperties`** (`@ConfigurationProperties(prefix="sentinel.header-audit")`):
  - `enabled` (default `true`), `trackedHeaders` (default list of 6 security/cache headers)
- **`HeaderAuditRegistry`** (`@Component`):
  - `ConcurrentHashMap<String, AtomicLong[]>` per-header `{present, absent}` pair-counters + `AtomicLong totalResponses`
  - `record(header, present)`, `incrementResponses()`, `snapshot()` → `{totalResponses, coveragePercent, headers}`, `reset()`
  - `coveragePercent` = `100 × totalPresent / (totalPresent + totalAbsent)` rounded to 1 decimal place
- **`HeaderAuditFilter`** (`WebFilter`, order `LOWEST_PRECEDENCE - 12`):
  - Uses `beforeCommit` to inspect response headers just before flush
  - Iterates all configured `trackedHeaders` and records presence/absence for each
  - Skips `/actuator/**` and `/admin/**` to prevent self-recording
- **`HeaderAuditController`** (`@RestController`, `/admin/header-audit`):
  - `GET /admin/header-audit` — `{enabled, trackedHeaders, totalResponses, coveragePercent, headers}`, requires `ROLE_ADMIN`
  - `POST /admin/header-audit/reset` — clears all counters

### Tests added
- **`HeaderAuditControllerTest`** (5 tests combining unit + integration, `@SpringBootTest RANDOM_PORT`):
  - expected fields, seeded present/absent counts appear correctly, coverage percentage computed correctly (75% for 3-present/1-absent)
  - reset clears counters, user role returns 403

---

## [0.50.0] – 2026-08-24 — Phase 50: Request Clock-Skew Detector

### Added
- **`ClockSkewProperties`** (`@ConfigurationProperties(prefix="sentinel.clock-skew")`):
  - `enabled` (default `true`), `headerName` (default `X-Request-Timestamp`), `toleranceSeconds` (default `300`), `maxRecords` (default `100`)
- **`SkewedRequestRecord`** (record): `detectedAt`, `method`, `path`, `clientTimestamp`, `skewSeconds`
- **`ClockSkewRegistry`** (`@Component`):
  - `AtomicLong totalChecked` and `totalSkewed`; `LinkedBlockingDeque` ring-buffer of recent flagged records
  - `recordChecked()`, `recordSkewed(rec)`, `snapshot()` → `{totalChecked, totalSkewed, recentSkewed}`, `reset()`
- **`ClockSkewFilter`** (`WebFilter`, order `LOWEST_PRECEDENCE - 11`):
  - Reads `X-Request-Timestamp` (configurable) from each request; parses as epoch-seconds
  - Flags requests where `|serverTime - clientTime| > toleranceSeconds`; purely observational — does not block
  - Silently ignores non-numeric timestamps; skips `/actuator/**` and `/admin/**`
- **`ClockSkewController`** (`@RestController`, `/admin/clock-skew`):
  - `GET /admin/clock-skew` — `{enabled, toleranceSeconds, headerName, totalChecked, totalSkewed, recentSkewed}`, requires `ROLE_ADMIN`
  - `POST /admin/clock-skew/reset` — clears all counters and history

### Tests added
- **`ClockSkewControllerTest`** (5 tests combining unit + integration, `@SpringBootTest RANDOM_PORT`):
  - expected fields, seeded skewed record appears in snapshot, reset clears counters
  - user role returns 403, within-tolerance records not counted as skewed

---

## [0.49.0] – 2026-08-24 — Phase 49: Top-N IP Address Tracker

### Added
- **`IpCounterProperties`** (`@ConfigurationProperties(prefix="sentinel.ip-counter")`):
  - `enabled` (default `true`), `topN` (default `20`)
- **`IpCounterRegistry`** (`@Component`):
  - `ConcurrentHashMap<String, AtomicLong>` per-IP counters + `AtomicLong total`
  - `record(ip)`, `snapshot(topN)` → `{total, uniqueIps, topIps}` sorted by count descending, `reset()`
  - `getTotal()` and `getUniqueIpCount()` helpers for tests
- **`IpCounterFilter`** (`WebFilter`, order `LOWEST_PRECEDENCE - 10`):
  - Extracts client IP: prefers first hop in `X-Forwarded-For` (real client IP behind proxy), falls back to remote address
  - Skips `/actuator/**` and `/admin/**` to prevent self-recording
- **`IpCounterController`** (`@RestController`, `/admin/ip-stats`):
  - `GET /admin/ip-stats` — `{enabled, topN, total, uniqueIps, topIps}`, requires `ROLE_ADMIN`
  - `POST /admin/ip-stats/reset` — clears all counters

### Tests added
- **`IpCounterControllerTest`** (5 tests combining unit + integration, `@SpringBootTest RANDOM_PORT`):
  - expected fields, seeded IPs appear sorted by count descending, reset clears counters
  - user role returns 403, `extractClientIp()` unit test verifying XFF first-hop extraction

---

## [0.48.0] – 2026-08-24 — Phase 48: Request User-Agent Distribution

### Added
- **`UserAgentProperties`** (`@ConfigurationProperties(prefix="sentinel.user-agent-stats")`):
  - `enabled` (default `true`)
- **`UserAgentRegistry`** (`@Component`):
  - `ConcurrentHashMap<String, AtomicLong>` per-category counters + `AtomicLong total`
  - `categorise(userAgent)` — normalises raw User-Agent strings into five categories: `bot`, `mobile`, `browser`, `service`, `unknown`; bot detection runs first to avoid misclassifying Googlebot (which contains "Safari")
  - `record(userAgent)`, `snapshot()` → `{total, categories}`, `reset()`
- **`UserAgentFilter`** (`WebFilter`, order `LOWEST_PRECEDENCE - 9`):
  - Reads `User-Agent` request header at filter entry time (no `beforeCommit` needed)
  - Skips `/actuator/**` and `/admin/**` to prevent self-recording of admin requests
- **`UserAgentController`** (`@RestController`, `/admin/user-agent-stats`):
  - `GET /admin/user-agent-stats` — `{enabled, total, categories}`, requires `ROLE_ADMIN`
  - `POST /admin/user-agent-stats/reset` — clears all counters

### Tests added
- **`UserAgentControllerTest`** (5 tests combining unit + integration, `@SpringBootTest RANDOM_PORT`):
  - expected fields, seeded categories appear in snapshot, reset clears counters
  - user role returns 403, `categorise()` utility unit test covering all five categories

---

## [0.47.0] – 2026-08-24 — Phase 47: Response Content-Type Distribution

### Added
- **`ContentTypeProperties`** (`@ConfigurationProperties(prefix="sentinel.content-type-stats")`):
  - `enabled` (default `true`)
- **`ContentTypeRegistry`** (`@Component`):
  - `ConcurrentHashMap<String, AtomicLong>` per-type counters + `AtomicLong total`
  - `normalise(contentType)` — strips parameters and lowercases (e.g. `"application/json;charset=UTF-8"` → `"application/json"`)
  - `record(contentType)`, `snapshot()` → `{total, types}`, `reset()`
- **`ContentTypeFilter`** (`WebFilter`, order `LOWEST_PRECEDENCE - 8`):
  - Uses `beforeCommit` to read response `Content-Type` before flush
  - Skips `/actuator/**` and `/admin/**`; records `null` as `"unknown"`
- **`ContentTypeController`** (`@RestController`, `/admin/content-type-stats`):
  - `GET /admin/content-type-stats` — `{enabled, total, types}`, requires `ROLE_ADMIN`
  - `POST /admin/content-type-stats/reset` — clears all counters

### Tests added
- **`ContentTypeControllerTest`** (5 tests combining unit + integration, `@SpringBootTest RANDOM_PORT`):
  - expected fields, seeded types normalised + aggregated correctly, reset clears
  - user role returns 403, `normalise()` utility unit test

---

## [0.46.0] – 2026-08-24 — Phase 46: HTTP Method Distribution Tracker

### Added
- **`MethodProperties`** (`@ConfigurationProperties(prefix="sentinel.method-stats")`):
  - `enabled` (default `true`)
- **`MethodRegistry`** (`@Component`):
  - `ConcurrentHashMap<String, AtomicLong>` per-method counters + `AtomicLong total`
  - `record(method)`, `snapshot()` → `{total, methods}`, `reset()`
- **`MethodFilter`** (`WebFilter`, order `LOWEST_PRECEDENCE - 7`):
  - Records HTTP method name on every non-actuator request
- **`MethodController`** (`@RestController`, `/admin/method-stats`):
  - `GET /admin/method-stats` — `{enabled, total, methods}`, requires `ROLE_ADMIN`
  - `POST /admin/method-stats/reset` — clears all counters

### Tests added
- **`MethodControllerTest`** (5 tests combining unit + integration, `@SpringBootTest RANDOM_PORT`):
  - expected fields, seeded GET/POST appear in snapshot, reset clears
  - user role returns 403, DELETE/PATCH unit recording and reset

---

## [0.45.0] – 2026-08-24 — Phase 45: Live Operations Dashboard

### Added
- **`LiveDashboardController`** (`@RestController`, `/admin/dashboard`):
  - `GET /admin/dashboard` — single endpoint aggregating 11 monitoring sections:
    - `timestamp` — snapshot time (ISO-8601)
    - `health` — composite score, grade, factors (from Phase 39)
    - `uptime` — startTime, uptimeSeconds, totalRequests (from Phase 30)
    - `concurrency` — current, peak, totalCompleted (from Phase 41)
    - `errors` — routeCount, totalRequests, totalErrors (from Phase 35)
    - `statusCodes` — total + 2xx/3xx/4xx/5xx/other buckets (from Phase 40)
    - `latency` — routeCount (from Phase 38)
    - `slowRequests` — recordedCount (from Phase 33)
    - `sessions` — activeCount (from Phase 27)
    - `sizes` — oversizedRequestCount, responseSizeSamples, responseSizeAvgBytes (Phases 37, 42)
    - `hops` — totalProcessed, totalRejected (from Phase 44)
  - Requires `ROLE_ADMIN`

### Tests added
- **`LiveDashboardControllerTest`** (4 integration tests, `@SpringBootTest RANDOM_PORT`):
  - all top-level sections present, health contains score/grade/factors, uptime contains uptimeSeconds
  - user role returns 403

---

## [0.44.0] – 2026-08-24 — Phase 44: Hop Count Guard

### Added
- **`HopCountProperties`** (`@ConfigurationProperties(prefix="sentinel.hop-count")`):
  - `enabled` (default `true`), `maxHops` (default `10`)
- **`HopCountRegistry`** (`@Component`):
  - `AtomicLong totalProcessed`, `AtomicLong totalRejected`
  - `ConcurrentHashMap<Integer, AtomicLong>` per-hop-count distribution
  - `recordProcessed(hopCount)`, `recordRejected(hopCount)`, `snapshot()`, `reset()`
- **`HopCountFilter`** (`WebFilter`, order `HIGHEST_PRECEDENCE + 6`):
  - Counts comma-separated entries across all `X-Forwarded-For` header values
  - Requests exceeding `maxHops` → HTTP 400 + JSON error body
  - Records every request (processed/rejected) in `HopCountRegistry`
- **`HopCountController`** (`@RestController`, `/admin/hop-stats`):
  - `GET /admin/hop-stats` — `{enabled, maxHops, totalProcessed, totalRejected, distribution}`, requires `ROLE_ADMIN`
  - `POST /admin/hop-stats/reset` — clears all counters

### Tests added
- **`HopCountFilterTest`** (7 integration tests, `@SpringBootTest RANDOM_PORT`):
  - no X-Forwarded-For passes, within-limit passes, exceeding limit → 400
  - filter disabled passes excessive hops, `countHops` utility parsing
  - admin endpoint fields, distribution correctly records hop counts

---

## [0.43.0] – 2026-08-24 — Phase 43: Request Path Length Guard

### Added
- **`PathLengthProperties`** (`@ConfigurationProperties(prefix="sentinel.path-length")`):
  - `enabled` (default `true`), `maxPathLength` (default `2048`), `maxQueryLength` (default `4096`), `maxRecords` (default `100`)
- **`PathLengthRecord`** (Java record): `timestamp, method, path, pathLength, queryLength, violation ("path"|"query")`
- **`PathLengthRegistry`** (`@Component`): ring-buffer, `record`, `snapshot`, `count`, `clear`
- **`PathLengthFilter`** (`WebFilter`, order `HIGHEST_PRECEDENCE + 5`):
  - Returns HTTP 414 for path violations, HTTP 400 for query string violations
  - Records each rejection in `PathLengthRegistry`
  - Truncates stored path to 200 chars for oversized paths
- **`PathLengthController`** (`@RestController`, `/admin/path-rejections`):
  - `GET /admin/path-rejections` — `{enabled, maxPathLength, maxQueryLength, count, records}`, requires `ROLE_ADMIN`
  - `POST /admin/path-rejections/clear` — empties the registry

### Tests added
- **`PathLengthFilterTest`** (7 integration tests, `@SpringBootTest RANDOM_PORT`):
  - normal path passes, path exceeding limit → 414, query exceeding limit → 400
  - filter disabled → long path passes (404, not 414), rejection recorded in registry
  - admin endpoint fields, clear endpoint empties registry

---

## [0.42.0] – 2026-08-24 — Phase 42: Response Body Size Tracking

### Added
- **`ResponseSizeProperties`** (`@ConfigurationProperties(prefix="sentinel.response-size")`):
  - `enabled` (default `true`), `maxRecords` (default `200`)
- **`ResponseSizeRegistry`** (`@Component`):
  - `LinkedBlockingDeque<Long>` ring-buffer for recent Content-Length samples
  - `AtomicLong totalBytes`, `AtomicLong sampleCount` running totals (never reset by ring eviction)
  - `record(bytes)`, `recentSamples()`, `getTotalBytes()`, `getSampleCount()`, `getAverageBytes()`, `reset()`
- **`ResponseSizeFilter`** (`WebFilter`, order `LOWEST_PRECEDENCE - 6`):
  - Uses `beforeCommit` to read response `Content-Length` header before flush
  - Skips `/actuator/**` and `/admin/**` (admin endpoint sizes not operationally meaningful)
  - Silently skips responses without `Content-Length` (chunked streaming)
- **`ResponseSizeController`** (`@RestController`, `/admin/response-sizes`):
  - `GET /admin/response-sizes` — `{enabled, sampleCount, totalBytes, averageBytes, recentSamples}`, requires `ROLE_ADMIN`
  - `POST /admin/response-sizes/reset` — clears all state

### Tests added
- **`ResponseSizeRegistryTest`** (5 unit tests):
  - empty state, single record, multi-record average, ring-buffer eviction preserving running totals, reset
- **`ResponseSizeControllerTest`** (4 integration tests, `@SpringBootTest RANDOM_PORT`):
  - expected fields, seeded samples reported correctly, reset clears, user role returns 403

---

## [0.41.0] – 2026-08-24 — Phase 41: Concurrent Request Monitor

### Added
- **`ConcurrentRequestProperties`** (`@ConfigurationProperties(prefix="sentinel.concurrent-requests")`):
  - `enabled` (default `true`)
- **`ConcurrentRequestRegistry`** (`@Component`):
  - `AtomicInteger current` — tracks in-flight request count
  - `AtomicInteger peak` — high-water mark updated via CAS spin-loop (thread-safe)
  - `AtomicLong totalCompleted` — total requests that have finished
  - `increment()`, `decrement()`, `resetPeak()` (resets peak to current and totalCompleted to 0)
- **`ConcurrentRequestFilter`** (`WebFilter`, order `HIGHEST_PRECEDENCE + 1`):
  - Increments before chain, decrements via `doFinally` after completion
  - Skips `/actuator/**` health probe traffic
- **`ConcurrentRequestController`** (`@RestController`, `/admin/concurrent-requests`):
  - `GET /admin/concurrent-requests` — `{enabled, current, peak, totalCompleted}`, requires `ROLE_ADMIN`
  - `POST /admin/concurrent-requests/reset-peak` — resets peak and total counter

### Tests added
- **`ConcurrentRequestRegistryTest`** (6 unit tests):
  - initial state, increment, decrement + totalCompleted, peak high-water mark
  - resetPeak, concurrent increments don't under-count peak
- **`ConcurrentRequestControllerTest`** (4 integration tests, `@SpringBootTest RANDOM_PORT`):
  - expected fields, non-negative values, resetPeak response, user role returns 403

---

## [0.40.0] – 2026-08-24 — Phase 40: Response Status Code Distribution

### Added
- **`StatusCodeProperties`** (`@ConfigurationProperties(prefix="sentinel.status-codes")`):
  - `enabled` (default `true`)
- **`StatusCodeRegistry`** (`@Component`):
  - Per-code `AtomicLong` counters via `ConcurrentHashMap<Integer, AtomicLong>`
  - Four range bucket counters: `2xx`, `3xx`, `4xx`, `5xx`, `other`
  - `record(statusCode)`, `snapshot()` → `{total, buckets, codes}`, `reset()`
- **`StatusCodeFilter`** (`WebFilter`, order `LOWEST_PRECEDENCE - 5`):
  - Uses `doFinally` to capture response status after chain completes
  - Skips `/actuator/**` to avoid polluting metrics with health probes
- **`StatusCodeController`** (`@RestController`, `/admin/status-codes`):
  - `GET /admin/status-codes` — `{enabled, total, buckets, codes}`, requires `ROLE_ADMIN`
  - `POST /admin/status-codes/reset` — clears all counters

### Tests added
- **`StatusCodeRegistryTest`** (6 unit tests):
  - empty registry, 2xx bucket, 4xx/5xx segregation, per-code accuracy, reset, 3xx bucket
- **`StatusCodeControllerTest`** (4 integration tests, `@SpringBootTest RANDOM_PORT`):
  - expected fields, seeded codes visible, reset clears, user role returns 403

---

## [0.39.0] – 2026-08-24 — Phase 39: Gateway Health Score

### Added
- **`HealthScoreResult`** (Java record): `score (0-100), grade (A/B/C/D/F), factors (Map<String,Integer>)`
- **`HealthScoreService`** (`@Service`):
  - Computes composite score from 3 equally-weighted factors:
    - `errorRate`: 100 − clamp(overallErrorRatePct × 5, 0, 100) — penalises 4xx/5xx traffic
    - `slowRequests`: 100 − clamp(slowRequestCount × 2, 0, 100) — penalises slow request build-up
    - `uptime`: 100 if uptimeSec ≥ 60, else proportional — penalises recent restarts
  - Grades: A ≥ 90, B ≥ 75, C ≥ 60, D ≥ 40, F < 40
  - Aggregates from `ErrorRateRegistry`, `SlowRequestRegistry`, `UptimeRegistry`
- **`HealthScoreController`** (`@RestController`, `/admin/health-score`):
  - `GET /admin/health-score` — returns `{score, grade, factors}`, requires `ROLE_ADMIN`

### Tests added
- **`HealthScoreServiceTest`** (6 unit tests):
  - no signals → high score, high error rate → reduces score, zero errors → factor 100
  - 50 slow requests → slow factor 0, grade boundary mapping, factors map contains all keys
- **`HealthScoreControllerTest`** (4 integration tests, `@SpringBootTest RANDOM_PORT`):
  - expected fields, score in 0-100 range, factors contain required keys, user role returns 403

---

## [0.38.0] – 2026-08-24 — Phase 38: Response Latency Percentiles

### Added
- **`LatencyProperties`** (`@ConfigurationProperties(prefix="sentinel.latency")`):
  - `enabled` (default `true`), `maxSamplesPerRoute` (default `1000`)
- **`LatencySnapshot`** (Java record): `p50Ms, p95Ms, p99Ms, sampleCount`; percentile computed via ceiling index
- **`LatencyRegistry`** (`@Component`):
  - Per-route `LinkedBlockingDeque<Long>` ring-buffers bounded by `maxSamplesPerRoute`
  - `record(route, durationMs)`, `snapshots()` → `Map<route, LatencySnapshot>`, `routeCount()`, `reset()`
  - Percentiles computed on a sorted copy at query time
- **`LatencyFilter`** (`WebFilter`, order `LOWEST_PRECEDENCE - 4`):
  - Captures start time before chain; records duration in `doFinally` after response completes
  - Buckets paths to 2 segments (same strategy as `ErrorRateFilter`)
  - Skips `/admin/**` and `/actuator/**`
- **`LatencyController`** (`@RestController`, `/admin/latency`):
  - `GET /admin/latency` — `{enabled, routeCount, routes}`, requires `ROLE_ADMIN`
  - `POST /admin/latency/reset` — clears all route buffers

### Tests added
- **`LatencyRegistryTest`** (7 unit tests):
  - empty registry, single-sample percentiles, multi-sample percentiles, ring-buffer eviction
  - multiple independent routes, reset, pathBucket utility
- **`LatencyControllerTest`** (4 integration tests, `@SpringBootTest RANDOM_PORT`):
  - expected fields, seeded samples visible, reset clears registry, user role returns 403

---

## [0.37.0] – 2026-08-24 — Phase 37: Request Size Guard

### Added
- **`RequestSizeProperties`** (`@ConfigurationProperties(prefix="sentinel.request-size")`):
  - `enabled` (default `true`), `maxBodyBytes` (default `10485760` = 10 MB), `maxRecords` (default `100`)
- **`OversizedRequestRecord`** (Java record): `timestamp, method, path, claimedBytes`
- **`OversizedRequestRegistry`** (`@Component`):
  - `LinkedBlockingDeque` ring-buffer bounded by `maxRecords`; `record`, `snapshot`, `count`, `clear`
- **`RequestSizeFilter`** (`WebFilter`, order `HIGHEST_PRECEDENCE + 4`):
  - Reads `Content-Length` header; rejects with HTTP 413 + JSON body if it exceeds `maxBodyBytes`
  - Records rejection in `OversizedRequestRegistry`; skips check when no `Content-Length` is present
  - Runs before Spring Security and the request sanitizer
- **`RequestSizeController`** (`@RestController`, `/admin/request-sizes`):
  - `GET /admin/request-sizes` — `{enabled, maxBodyBytes, count, records}`, requires `ROLE_ADMIN`
  - `POST /admin/request-sizes/clear` — empties the registry

### Tests added
- **`OversizedRequestRegistryTest`** (6 unit tests):
  - empty registry, single record, snapshot contents, ring-buffer eviction, clear, immutable snapshot
- **`RequestSizeFilterTest`** (7 integration tests, `@SpringBootTest RANDOM_PORT`):
  - within-limit body passes, oversized body returns 413, registry records rejection
  - filter disabled passes large body, GET without Content-Length passes
  - admin endpoint fields, clear endpoint empties registry

---

## [0.36.0] – 2026-08-24 — Phase 36: JWT Expiry Warning

### Added
- **`JwtExpiryProperties`** (`@ConfigurationProperties(prefix="sentinel.jwt-expiry-warning")`):
  - `enabled` (default `true`), `warningWindowSeconds` (default `300`), `headerName` (default `X-JWT-Expires-In`)
- **`JwtExpiryFilter`** (`WebFilter`, order `1`):
  - Uses `exchange.getResponse().beforeCommit(...)` to read `ReactiveSecurityContextHolder` and set header before response flushes
  - Adds `{headerName}: {secondsRemaining}` to response when JWT `exp` is within `warningWindowSeconds` of now
  - Skips non-JWT requests silently

### Tests added
- **`JwtExpiryFilterTest`** (5 integration tests, `@SpringBootTest RANDOM_PORT`):
  - JWT expiring in 60s (< 300s window) → header added
  - JWT expiring in 3600s (> 300s window) → header absent
  - Filter disabled → no header even for soon-expiring JWT
  - Custom header name → custom header appears in response
  - JWT at exact boundary (300s) → header added (≤ comparison)

---

## [0.35.0] – 2026-08-24 — Phase 35: Error Rate Tracking

### Added
- **`ErrorRateRegistry`** (`@Component`):
  - Per-path `{total, errors}` counters using `ConcurrentHashMap<String, RouteStats>` with `AtomicLong`
  - `record(route, statusCode)` — increments total; increments errors if statusCode ≥ 400
  - `snapshots()` — returns `Map<route, {total, errors, errorRatePct}>` with rounded percentage
  - `routeCount()`, `reset()`
- **`ErrorRateFilter`** (`WebFilter`, order `LOWEST_PRECEDENCE - 3`):
  - Uses `doFinally` to capture response status after chain completes
  - Groups paths to 2-segment buckets: `/api/users/123` → `/api/users` (avoids cardinality explosion)
  - Skips `/admin/**` and `/actuator/**`
- **`ErrorRateController`** (`@RestController`, `/admin/error-rates`):
  - `GET /admin/error-rates` — returns `{routeCount, routes}`, requires `ROLE_ADMIN`
  - `POST /admin/error-rates/reset` — clears all counters

### Tests added
- **`ErrorRateRegistryTest`** (8 unit tests):
  - 200 → total only, 500/400 → error counted, 50% error rate, multi-route, reset, pathBucket grouping
- **`ErrorRateControllerTest`** (4 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `getErrorRates_admin_returnsExpectedFields`, `seededErrors_appearsInSnapshot`
  - `resetCounters_clearsRegistry`, `getErrorRates_userRole_returns403`

---

## [0.34.0] – 2026-08-24 — Phase 34: Config Summary Dashboard

### Added
- **`ConfigSummaryController`** (`@RestController`, `/admin/config`):
  - `GET /admin/config` — single-endpoint dashboard showing:
    - `startTime` — gateway start timestamp (ISO-8601)
    - `features` — map of feature name → enabled boolean, covering: maintenance, ipAccess, securityHeaders, requestSanitizer, slowRequestDetection, slaTracking, claimAuthorization, jwtAudienceValidation
    - `stats` — live snapshot: uptimeSeconds, requestCount, totalTrackedSessions, inflightRequests, totalCompletedRequests
  - Requires `ROLE_ADMIN`

### Tests added
- **`ConfigSummaryControllerTest`** (4 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `getConfig_admin_returnsTopLevelKeys` — body has startTime, features, stats
  - `getConfig_featuresMap_containsAllToggles` — all 8 feature toggle keys present
  - `getConfig_statsMap_containsMetricKeys` — all 5 metric keys present, uptimeSeconds ≥ 0
  - `getConfig_userRole_returns403`

---

## [0.33.0] – 2026-08-24 — Phase 33: Slow Request Detection

### Added
- **`SlowRequestRecord`** — immutable record: `timestamp`, `method`, `path`, `durationMs`, `status`
- **`SlowRequestProperties`** (`@Component`, `@ConfigurationProperties("sentinel.slow-request")`):
  - `enabled: boolean` (default `true`)
  - `thresholdMs: long` (default `2000`) — minimum duration to record
  - `maxRecords: int` (default `100`) — ring-buffer capacity
- **`SlowRequestRegistry`** (`@Component`):
  - `LinkedBlockingDeque` ring buffer initialized from `maxRecords`
  - `record()`, `all()`, `size()`, `clear()`
- **`SlowRequestFilter`** (`WebFilter`, order `LOWEST_PRECEDENCE - 2`):
  - Captures start time before chain, then uses `doFinally` to measure duration
  - Records if `durationMs >= thresholdMs`; skips `/admin/**` and `/actuator/**`
- **`SlowRequestController`** (`@RestController`, `/admin/slow-requests`):
  - `GET /admin/slow-requests` — returns `{enabled, thresholdMs, total, records}`, requires `ROLE_ADMIN`
  - `POST /admin/slow-requests/clear` — clears log, returns `{cleared, remaining}`
- **`application.yml`** (main and test): `sentinel.slow-request.*` block added

### Tests added
- **`SlowRequestRegistryTest`** (6 unit tests):
  - `freshRegistry_isEmpty`, `record_addsEntry`, `all_returnsRecordsInOrder`
  - `clear_removesAllEntries`, `ringBuffer_evictsOldestWhenFull`, `record_capturesAllFields`
- **`SlowRequestControllerTest`** (5 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `getSlowRequests_admin_returnsExpectedFields`, `seededRecords_appearsInLog`
  - `clearLog_emptiesRegistry`, `getSlowRequests_userRole_returns403`
  - `filterDisabledConfig_reflectedInResponse`

---

## [0.32.0] – 2026-08-24 — Phase 32: Request Header Sanitizer

### Added
- **`RequestSanitizerProperties`** (`@Component`, `@ConfigurationProperties("sentinel.request-sanitizer")`):
  - `enabled: boolean` (default `true`)
  - `maxHeaderValueLength: int` (default `8192`) — reject header values exceeding this byte count
  - `blockNullBytes: boolean` (default `true`) — reject header values containing null bytes (`\0`)
  - Built-in skip list for standard/infrastructure headers: `Authorization`, `Cookie`, `User-Agent`, `Host`, `X-Request-ID`, `traceparent`, `X-Forwarded-For`, etc.
  - `isSkipped(headerName)` — case-insensitive lookup against skip list
- **`RequestSanitizerFilter`** (`WebFilter`, order `HIGHEST_PRECEDENCE + 3`):
  - Iterates all non-skipped request headers; rejects with 400 JSON if length or null-byte violations found
  - Error body: `{"status":400,"error":"Bad Request","detail":"Header value too long: <name> (max N chars)"}`
- **`RequestSanitizerController`** (`@RestController`, `/admin/request-sanitizer`):
  - `GET /admin/request-sanitizer` — returns `{enabled, maxHeaderValueLength, blockNullBytes}`, requires `ROLE_ADMIN`
- **`application.yml`** (main and test): `sentinel.request-sanitizer.*` block added

### Tests added
- **`RequestSanitizerPropertiesTest`** (6 unit tests):
  - Default values, setters, `isSkipped` for standard and custom headers
- **`RequestSanitizerFilterTest`** (7 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `normalRequest_passes`, `oversizedHeader_returns400`
  - `headerBelowLimit_passes`, `nullByteInHeader_returns400`
  - `nullByteCheckDisabled_passes`, `filterDisabled_oversizedHeaderPasses`
  - `getConfig_admin_returnsExpectedFields`

---

## [0.31.0] – 2026-08-24 — Phase 31: Admin Operation Audit Trail

### Added
- **`AdminAuditRecord`** — immutable record: `timestamp`, `subject`, `method`, `path`, `status`
- **`AdminAuditRegistry`** (`@Component`):
  - `LinkedBlockingDeque<AdminAuditRecord>` ring buffer with 500-entry cap
  - Oldest entry evicted when full (ring-buffer semantics)
  - `record()`, `all()`, `size()`, `clear()`
- **`AdminAuditFilter`** (`WebFilter`, order `LOWEST_PRECEDENCE - 1`):
  - Intercepts all `/admin/**` requests except `/admin/audit-log/**` (self-exclusion)
  - Records subject (from JWT or "anonymous"), method, path, and response status after chain completes
- **`AdminAuditController`** (`@RestController`, `/admin/audit-log`):
  - `GET /admin/audit-log?limit=100` — returns `{total, returned, records}`, newest N via limit param
  - `POST /admin/audit-log/clear` — clears the in-memory log, returns `{cleared, remaining}`
  - Requires `ROLE_ADMIN`

### Tests added
- **`AdminAuditRegistryTest`** (6 unit tests):
  - `freshRegistry_isEmpty`, `record_addsEntry`, `all_returnsRecordsInOrder`
  - `clear_removesAllRecords`, `ringBuffer_evictsOldestWhenFull`, `record_capturesAllFields`
- **`AdminAuditControllerTest`** (5 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `getAuditLog_admin_returnsExpectedFields`, `seededRecords_appearsInLog`
  - `limitParameter_restrictedResults`, `clearLog_emptiesRegistry`
  - `getAuditLog_userRole_returns403`

---

## [0.30.0] – 2026-08-24 — Phase 30: Gateway Uptime Tracking

### Added
- **`UptimeRegistry`** (`@Component`):
  - `startTime: Instant` — captured at bean construction (immutable)
  - `requestCount: AtomicLong` — thread-safe counter incremented per non-admin/non-actuator request
  - `recordRequest()`, `getRequestCount()`, `uptimeSeconds()`, `reset()` (counter only, not startTime)
- **`UptimeFilter`** (`WebFilter`, order `HIGHEST_PRECEDENCE + 1`):
  - Counts all inbound requests; skips `/admin/**` and `/actuator/**` to avoid inflating metrics
- **`UptimeController`** (`@RestController`, `/admin/uptime`):
  - `GET /admin/uptime` — returns `{startTime, uptimeSeconds, requestCount}`, requires `ROLE_ADMIN`
  - `POST /admin/uptime/reset` — clears request counter, returns `{reset: true, requestCount: 0}`

### Tests added
- **`UptimeRegistryTest`** (7 unit tests):
  - `freshRegistry_startTimeIsRecent`, `freshRegistry_requestCountIsZero`
  - `recordRequest_incrementsCount`, `uptimeSeconds_isNonNegative`
  - `reset_clearsRequestCount`, `reset_doesNotAffectStartTime`
  - `concurrentRecordRequest_threadsafe` — 10 threads × 100 increments = 1000
- **`UptimeControllerTest`** (4 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `getUptime_admin_returnsExpectedFields`
  - `resetCounter_returnsZeroRequestCount`
  - `seededRequests_appearsInRequestCount`
  - `getUptime_userRole_returns403`

---

## [0.29.0] – 2026-08-24 — Phase 29: Response Security Headers

### Added
- **`SecurityHeadersProperties`** (`@Component`, `@ConfigurationProperties("sentinel.security-headers")`):
  - `enabled: boolean` (default `true`)
  - `headers: Map<String, String>` — default OWASP headers: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, `X-XSS-Protection: 0`, `Permissions-Policy: interest-cohort=()`
  - `effectiveHeaders()` — merges configured map, drops blank-value entries (caller opts out of that header)
- **`SecurityHeadersFilter`** (`WebFilter`, order `LOWEST_PRECEDENCE`):
  - Registers a `beforeCommit` hook so headers are applied immediately before the response is flushed
  - Force-sets all effective headers using `HttpHeaders.set()`, overriding Spring Security defaults (e.g., `Referrer-Policy: no-referrer` → `strict-origin-when-cross-origin`)
  - Disabled by setting `sentinel.security-headers.enabled: false`
- **`SecurityHeadersController`** (`@RestController`, `/admin/security-headers`):
  - `GET /admin/security-headers` — returns `{enabled, headers}` showing active config, requires `ROLE_ADMIN`
- **`application.yml`** (main and test): `sentinel.security-headers.enabled: true` block added

### Tests added
- **`SecurityHeadersPropertiesTest`** (6 unit tests):
  - Default 5 headers present, xContentTypeOptions default, referrerPolicy default, custom header, blank removal, toggle enabled
- **`SecurityHeadersFilterTest`** (6 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `defaultHeaders_referrerPolicyOverridesSpringSecurityValue` — `Referrer-Policy: strict-origin-when-cross-origin` overrides Spring Security's `no-referrer`
  - `defaultHeaders_permissionsPolicyPresent` — `Permissions-Policy: interest-cohort=()` added (Spring Security doesn't set this)
  - `filterDisabled_permissionsPolicyAbsent` — disabled filter doesn't add `Permissions-Policy`
  - `customHeader_appearsInResponse` — extra header appears in response
  - `blankValueOverride_permissionsPolicyRemoved` — blank-value config entry removes that header
  - `getConfig_admin_returnsExpectedFields` — admin endpoint returns correct JSON

---

## [0.28.0] – 2026-08-24 — Phase 28: IP-Based Access Control

### Added
- **`IpAccessProperties`** (`@Component`, `@ConfigurationProperties("sentinel.ip-access")`):
  - `enabled: boolean` (default `false`)
  - `mode: Mode` — `ALLOWLIST` (only listed IPs pass) or `DENYLIST` (listed IPs blocked), default `DENYLIST`
  - `globalList: List<String>` — IP addresses subject to the access rule
  - `routes: Map<String, List<String>>` — per-route IP list overrides (for future path-based extension)
  - `effectiveListForRoute(routeId)` helper returns route-specific list or falls back to globalList
- **`IpAccessFilter`** (`WebFilter`, order `HIGHEST_PRECEDENCE + 2`):
  - Applies globally to all requests (admin endpoints + proxied routes)
  - Client IP extracted from `X-Forwarded-For` header (first entry) or remote address
  - ALLOWLIST mode: IP not in globalList → 403 JSON `{"status":403,"error":"IP access denied","ip":"...","mode":"ALLOWLIST"}`
  - DENYLIST mode: IP in globalList → 403 JSON
  - Disabled by default; toggled at runtime via `setEnabled()`
- **`IpAccessController`** (`@RestController`, `/admin/ip-access`):
  - `GET /admin/ip-access` — returns `{enabled, mode, globalList, routes}`, requires `ROLE_ADMIN`
- **`application.yml`** (main and test): `sentinel.ip-access.enabled: false` block added

### Tests added
- **`IpAccessPropertiesTest`** (7 unit tests):
  - Default values, effectiveListForRoute with/without route override, mode setter
- **`IpAccessFilterTest`** (7 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `filterDisabled_requestPasses` — disabled filter lets all through
  - `denylist_ipInList_returns403` — blocked IP via X-Forwarded-For gets 403
  - `denylist_ipNotInList_passes` — non-listed IP passes denylist
  - `allowlist_ipInList_passes` — listed IP passes allowlist
  - `allowlist_ipNotInList_returns403` — non-listed IP blocked in allowlist mode
  - `xForwardedFor_firstEntryUsed` — first entry of multi-hop XFF header used as client IP
  - `getConfig_admin_returnsExpectedFields` — admin endpoint returns all config fields

---

## [0.27.0] – 2026-08-24 — Phase 27: Active Session Tracking

### Added
- **`ActiveSessionRegistry`** (`@Component`):
  - `ConcurrentHashMap<String, Instant>` — maps JWT subject → last-seen timestamp
  - `recordActivity(String subject)` — updates timestamp; null/blank subjects ignored
  - `activeSince(Instant since)` — returns subjects seen at or after `since` (sliding window)
  - `totalTracked()` — total subjects in registry
  - `evictBefore(Instant cutoff)` — removes entries last seen before cutoff
  - `clear()` — wipes entire registry
- **`SessionTrackingFilter`** (`GlobalFilter`, order `LOWEST_PRECEDENCE - 1`):
  - Runs after chain completes; reads security context via `ReactiveSecurityContextHolder`
  - Records JWT `sub` claim via `JwtAuthenticationToken` instanceof check
  - Non-JWT (API key, unauthenticated) requests are silently skipped
- **`SessionController`** (`@RestController`, `/admin/sessions`):
  - `GET /admin/sessions?windowSeconds=300` — returns `{windowSeconds, activeSessions, subjects, totalTracked}`
  - `POST /admin/sessions/evict?olderThanSeconds=3600` — evicts stale entries, returns `{evicted, remaining}`
  - Both endpoints require `ROLE_ADMIN`

### Tests added
- **`ActiveSessionRegistryTest`** (10 unit tests):
  - `freshRegistry_isEmpty`, `recordActivity_tracksSubject`, null/blank subject ignored
  - `activeSince_recentActivity_returnsSubject`, `activeSince_futureInstant_returnsEmpty`
  - `evictBefore_removesOldEntries`, `evictBefore_keepsRecentEntries`
  - `clear_removesAll`, `multipleSubjects_trackedIndependently`
- **`SessionControllerTest`** (5 integration tests, `@SpringBootTest RANDOM_PORT`):
  - `getActiveSessions_admin_returns200WithExpectedFields`
  - `getActiveSessions_customWindow_reflected`
  - `seededSubject_appearsInActiveSessions`
  - `evict_admin_returnsEvictedCount`
  - `getActiveSessions_userRole_returns403`

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
