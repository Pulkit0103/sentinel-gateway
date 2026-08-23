# Security Pipeline

Every request Sentinel Gateway receives passes through this exact pipeline in order. A rejection at any stage returns a structured error response and short-circuits the rest of the chain — the request never reaches a downstream service.

## Layer-by-Layer Walkthrough

### Layer 1 — TLS Termination

All external traffic must arrive over HTTPS. The gateway terminates TLS and forwards plaintext to downstream services over a trusted internal network (Docker bridge network in Compose; service mesh in Kubernetes).

**No plaintext traffic is accepted from untrusted networks.**

---

### Layer 2 — Request ID Assignment (`RequestIdFilter`)

Every request receives two correlation headers before any other processing:

| Header | Value |
|---|---|
| `X-Request-ID` | UUID unique to this request |
| `X-Correlation-ID` | Caller-supplied if present; generated otherwise |

These IDs appear in all log entries and audit events, enabling full request tracing across services.

---

### Layer 3 — Authentication

The gateway supports two authentication mechanisms. A request must use exactly one.

#### 3a. JWT / OAuth2 / OIDC

The client presents a Bearer token issued by Keycloak.

**Validation steps:**
1. Token signature — verified against Keycloak's JWKS endpoint
2. Issuer (`iss`) — must match configured `sentinel.security.jwt.issuer`
3. Audience (`aud`) — must include the gateway's client ID
4. Expiration (`exp`) — must not be expired
5. Not-before (`nbf`) — current time must be past this value

The gateway **never trusts** user ID or role claims passed in request headers by the caller.

#### 3b. API Keys

Machine-to-machine auth via `X-API-Key` header.

- Key is looked up by hash in PostgreSQL
- Stored as **bcrypt hash** — raw key is never stored
- Key status must be `ACTIVE` (not `REVOKED` or `EXPIRED`)
- Key carries `clientId`, `tenantId`, scopes, and expiry

**→ 401 Unauthorized if authentication is missing or invalid.**

---

### Layer 4 — Authorization: RBAC + Scopes (`RouteAuthorizationFilter`)

Authorization is evaluated after identity is established.

#### Roles

| Role | Description |
|---|---|
| `ADMIN` | Full access to all routes including admin endpoints |
| `USER` | Standard end-user access |
| `SUPPORT` | Read-only cross-tenant access |
| `SERVICE` | Machine-to-machine service identity |

#### Permissions (examples)

| Permission | Applies to |
|---|---|
| `USER_READ` | `GET /api/users/**` |
| `USER_WRITE` | `POST/PUT/PATCH /api/users/**` |
| `ORDER_READ` | `GET /api/orders/**` |
| `PAYMENT_WRITE` | `POST /api/payments/**` |

Each route in `application.yml` can declare `required-scopes`. If the authenticated principal lacks a required scope, the request is rejected.

**→ 403 Forbidden if authenticated but lacking authorization.**

---

### Layer 5 — Tenant Isolation (`TenantIsolationFilter`)

Every request carries a verified tenant context. The tenant ID is derived from the validated JWT claim or API key record — **never from an arbitrary `X-Tenant-ID` header** supplied by the caller.

- Tenant A cannot access Tenant B's resources.
- Cross-tenant access is only permitted for `ADMIN` or `SUPPORT` roles with explicit permission.

**→ 403 Forbidden on tenant mismatch.**

---

### Layer 6 — Security Policy Engine (`PolicyEnforcementFilter`)

Routes carry optional security policies defined in `application.yml` (or the database in future phases). A policy can require:

| Policy field | Effect |
|---|---|
| `required-scopes` | Additional OAuth2 scopes beyond the route default |
| `require-mfa` | Request must carry MFA claim in JWT |
| `request-signing-required` | HMAC signing must be present and valid |
| `allowed-methods` | HTTP methods not in this list → 405 Method Not Allowed |
| `rate-limit-policy` | Which rate limit tier to apply (ANONYMOUS / USER / PREMIUM) |

Example payment-service policy:
```yaml
- policy-id: payment-service-policy
  route-id: payment-service
  require-mfa: false
  request-signing-required: false
  required-scopes: []
  allowed-methods: [GET, POST]
  rate-limit-policy: PREMIUM
```

**→ 403 POLICY_VIOLATION if any check fails.**

---

### Layer 7 — Threat Detection (`ThreatDetectionFilter`)

The threat detector inspects the request path and headers against WAF-style patterns. Each matched pattern adds to a risk score.

| Detector | Signal | Score |
|---|---|---|
| `PathTraversalDetector` | `../`, `%2e%2e` etc. | +50 |
| `SqlInjectionDetector` | SQL keywords, `'OR 1=1'` etc. | +40 |
| `XssDetector` | `<script>`, `javascript:` etc. | +30 |
| `CommandInjectionDetector` | `;ls`, `|cat`, `$(...)` etc. | +40 |

| Score | Action |
|---|---|
| 0 – threshold(log) | ALLOW |
| threshold(log) – threshold(block) | ALLOW + log warning |
| threshold(block) – threshold(alert) | BLOCK → 400 THREAT_DETECTED |
| ≥ threshold(alert) | BLOCK + high-severity alert logged |

Default thresholds (configurable):
- Log: 25
- Block: 60
- Alert: 90

**→ 400 THREAT_DETECTED if score ≥ block threshold.**

---

### Layer 8 — Rate Limiting (`RateLimitFilter`)

Distributed token bucket implemented with Redis Lua scripts (atomic, race-free across gateway instances).

**Bucket key scoping:**
- Unauthenticated request → client IP
- Authenticated request → `userId:tenantId`
- API key request → `clientId`

**Rate limit tiers:**

| Tier | Requests per minute |
|---|---|
| `ANONYMOUS` | 100 |
| `USER` | 1,000 |
| `PREMIUM` | 10,000 |
| `DEFAULT` | 1,000 |

**Response headers always set:**

| Header | Value |
|---|---|
| `X-RateLimit-Limit` | Bucket capacity |
| `X-RateLimit-Remaining` | Tokens remaining after this request |
| `X-RateLimit-Reset` | Unix epoch when bucket refills |
| `Retry-After` | Seconds to wait (on 429 only) |

**→ 429 Too Many Requests when bucket is empty.**

---

### Layer 9 — Quota Enforcement (`QuotaEnforcementFilter`)

Quotas operate at a coarser time grain than rate limits — they enforce tenant-level daily and monthly request budgets.

- Counters stored in Redis (fast) with config in PostgreSQL (durable)
- Default daily limit: 100,000 requests
- Default monthly limit: 2,000,000 requests

**→ 429 QUOTA_EXCEEDED when tenant budget is exhausted.**

---

### Layer 10 — HMAC Request Signing (`HmacVerificationFilter`)

Sensitive routes can require HMAC-SHA256 request signing (opt-in per route via policy).

**Required request headers:**

| Header | Value |
|---|---|
| `X-Client-Id` | Signing client identifier |
| `X-Timestamp` | Unix epoch in ms; must be within ±5 minutes |
| `X-Nonce` | Unique per-request value |
| `X-Signature` | HMAC-SHA256 of `{method}\n{path}\n{timestamp}\n{nonce}\n{body-hash}` |

**Replay prevention:**
1. Timestamp must be within the validity window (default ±5 min)
2. Nonce must not appear in the Redis nonce store (stored with TTL = validity window)

A reused nonce returns **403 REPLAY_DETECTED**.

---

### After Routing — Audit and Metrics

These run *after* the downstream response is returned. They never block the request.

**Audit event** (published to Kafka topic `sentinel.audit.requests`):
```json
{
  "requestId": "req-abc-123",
  "timestamp": "2024-01-15T10:30:00Z",
  "tenantId": "tenant-acme",
  "userId": "user-42",
  "method": "POST",
  "path": "/api/payments",
  "authenticationType": "JWT",
  "authorizationOutcome": "ALLOWED",
  "sourceIp": "203.0.113.1",
  "riskScore": 0,
  "durationMs": 12,
  "status": 200
}
```

**Prometheus metrics** recorded per request:
- `gateway.requests.total` (counter, tagged by route, method, status)
- `gateway.request.duration` (histogram, p50/p95/p99 latency)
- `gateway.ratelimit.rejected.total`
- `gateway.threat.blocked.total`
