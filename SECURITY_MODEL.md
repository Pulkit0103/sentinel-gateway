# Sentinel Gateway — Security Model

## Core Principle: Zero Trust

Sentinel Gateway operates on the assumption that **no request is trusted by default**. Every request, regardless of its source network, must prove its identity and authorization before reaching a protected downstream service.

This principle applies to:
- External client traffic
- Internal service-to-service traffic
- Administrative requests

---

## Security Layers

### Layer 1 — Transport Security (TLS)

All external traffic must arrive over TLS. The gateway terminates TLS and forwards plaintext to downstream services over a trusted internal network. In Kubernetes, service mesh mutual TLS (mTLS) protects internal hops.

**Rule:** No plaintext traffic accepted from untrusted networks.

---

### Layer 2 — Authentication

The gateway supports multiple authentication mechanisms. A request must use exactly one:

#### 2a. JWT (OAuth2/OIDC — Phase 3+)

Tokens are issued by Keycloak. The gateway validates:
- **Signature** — using the issuer's public key from JWKS endpoint
- **Issuer (`iss`)** — must match configured Keycloak issuer URI
- **Audience (`aud`)** — must include the gateway's configured client ID
- **Expiration (`exp`)** — must not be expired
- **Not Before (`nbf`)** — must be past the not-before time
- **Scopes/Claims** — extracted for downstream authorization

The gateway **never trusts client-supplied user IDs or role claims** in request headers.

#### 2b. API Keys (Phase 6+)

Machine-to-machine authentication via `X-API-Key` header.

- Keys are stored as **bcrypt hashes** in PostgreSQL — raw keys are never stored
- Each key is associated with a `clientId`, `tenantId`, scopes, and expiry
- Key states: ACTIVE, REVOKED, EXPIRED
- The raw key is shown only once at creation time

**Rule:** A missing or invalid authentication → 401 Unauthorized.

---

### Layer 3 — Authorization (RBAC + Scopes)

Authorization is evaluated after authentication.

#### Roles

| Role    | Description                            |
|---------|----------------------------------------|
| ADMIN   | Full administrative access             |
| USER    | Standard end-user access               |
| SUPPORT | Read-only access across tenants        |
| SERVICE | Machine-to-machine service identity    |

#### Permissions

Permissions are mapped to route policies. Example:

| Permission     | Allowed on                    |
|----------------|-------------------------------|
| USER_READ      | GET /api/users/**             |
| USER_WRITE     | POST/PUT/PATCH /api/users/**  |
| ORDER_READ     | GET /api/orders/**            |
| ORDER_WRITE    | POST/PUT/PATCH /api/orders/** |
| PAYMENT_READ   | GET /api/payments/**          |
| PAYMENT_WRITE  | POST /api/payments/**         |

**Rule:** Authenticated but insufficient authorization → 403 Forbidden.

---

### Layer 4 — Tenant Isolation (Phase 7+)

Every request carries a verified tenant identity. The tenant context is derived from the validated JWT or API key — never from an arbitrary `X-Tenant-ID` header supplied by the caller.

Cross-tenant access is denied unless the caller holds an explicit cross-tenant role (e.g., SUPPORT or ADMIN with explicit permission).

**Rule:** Tenant A cannot access Tenant B's resources. Attempts → 403 Forbidden.

---

### Layer 5 — Security Policy Engine (Phase 11+)

Routes may carry additional security policies:

```json
{
  "route": "/api/payments/**",
  "allowedMethods": ["POST"],
  "requiredScopes": ["payment:write"],
  "requireMfa": true,
  "rateLimitPolicy": "PAYMENT_STANDARD",
  "requestSigningRequired": true
}
```

Policies are evaluated per request before routing to the downstream service.

---

### Layer 6 — Threat Detection (Phase 12+)

The gateway maintains a risk score per request. Suspicious patterns increase the score:

| Signal                          | Score Impact |
|---------------------------------|-------------|
| Path traversal pattern          | +50         |
| SQL injection pattern           | +40         |
| XSS pattern                     | +30         |
| Command injection pattern       | +40         |
| Known malicious IP              | +40         |
| Excessive auth failures         | +20         |
| Abnormal request rate           | +20         |
| Malformed headers               | +15         |

| Score Range | Action           |
|-------------|------------------|
| 0–30        | ALLOW            |
| 31–60       | ALLOW + LOG      |
| 61–80       | BLOCK            |
| 81+         | BLOCK + ALERT    |

**Note:** This is demonstrable threat detection for portfolio purposes. It is not equivalent to a commercial WAF.

---

### Layer 7 — Rate Limiting (Phase 8+)

Distributed rate limiting uses Redis token buckets. Keys are scoped to:
- Client IP (anonymous requests)
- User + Tenant (authenticated requests)
- Client ID (API key requests)

| Class     | Limit          |
|-----------|----------------|
| Anonymous | 100 req/min    |
| USER      | 1000 req/min   |
| PREMIUM   | 10000 req/min  |

**Rate limit headers returned:**
- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`
- `X-RateLimit-Reset`
- `Retry-After` (on 429)

---

### Layer 8 — Quotas (Phase 9+)

Quotas operate at a coarser grain than rate limits:

- Tenant-level daily request quota
- Stored in Redis (counter) + PostgreSQL (configuration)
- Quota exhausted → 429 Too Many Requests

---

### Layer 9 — Request Signing and Replay Prevention (Phase 10+)

Sensitive APIs may require HMAC-SHA256 request signing.

**Required headers:**
- `X-Client-Id` — identifies the signing client
- `X-Timestamp` — Unix epoch (ms); must be within ±5 minutes
- `X-Nonce` — unique value per request; stored in Redis with TTL
- `X-Signature` — HMAC-SHA256 of: `{method}\n{path}\n{timestamp}\n{nonce}\n{body-hash}`

**Replay prevention:**
1. Timestamp must be within the validity window
2. Nonce must not have been seen before (Redis nonce store with TTL = validity window)

A reused nonce → 403 Forbidden (replay detected).

---

## Error Response Format

All error responses follow a consistent format. Internal details are never exposed to clients:

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

**Never exposed:** stack traces, internal service URLs, database errors, infrastructure details, secret values.

---

## Secrets Management

| Secret Type              | Storage                         |
|--------------------------|---------------------------------|
| Database passwords       | Environment variables / K8s Secrets |
| Redis password           | Environment variables / K8s Secrets |
| Keycloak client secret   | Environment variables / K8s Secrets |
| JWT signing keys         | Keycloak-managed                |
| API key raw values       | Never stored; shown once at creation |
| API key hashes           | PostgreSQL (bcrypt)             |

`.env` files are excluded by `.gitignore`. Only `.env.example` (with placeholder values) is committed.

---

## Audit Logging (Phase 13+)

Every security decision generates an immutable audit event:

```json
{
  "requestId": "req-abc-123",
  "timestamp": "2024-01-15T10:30:00Z",
  "tenantId": "tenant-acme",
  "userId": "user-42",
  "clientId": "mobile-app-v2",
  "method": "POST",
  "path": "/api/payments",
  "authenticationType": "JWT",
  "authorizationOutcome": "DENIED",
  "denialReason": "MISSING_SCOPE",
  "sourceIp": "203.0.113.1",
  "riskScore": 20,
  "durationMs": 12
}
```

Audit events are published asynchronously to Kafka and are **never** allowed to block request processing.

---

## Known Limitations (Phase 1)

The current bootstrap phase enforces **no security controls**. It is a foundational structure only.

Security features are introduced incrementally starting from Phase 3.

Do not expose the Phase 1 gateway to a public network.
