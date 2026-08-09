# Sentinel Gateway — Threat Model

## Scope

This document covers threats to the Sentinel Gateway in production deployments.
The gateway is the single entry point for all API traffic; it enforces authentication,
authorization, rate limiting, tenant isolation, and request validation for all downstream services.

**In scope:** gateway process, its data stores (PostgreSQL, Redis), its auth provider (Keycloak),
inbound traffic from the internet, and privileged admin access.

**Out of scope:** downstream service internals, Keycloak internals, network-layer DDoS.

---

## Assets

| Asset | Sensitivity | Notes |
|-------|-------------|-------|
| JWT access tokens | HIGH | Grants API access; short-lived (300s default) |
| API key raw values | CRITICAL | Only shown once at creation; stored as SHA-256 hash |
| HMAC shared secret | CRITICAL | Gateway config; compromise allows signature forgery |
| PostgreSQL credentials | HIGH | Access to API key hashes and security policy data |
| Redis | MEDIUM | Rate limit counters; HMAC nonce store; replay window |
| Keycloak admin credentials | HIGH | Can create users with arbitrary roles |
| Audit log stream (Kafka) | MEDIUM | Contains IP addresses, user IDs, request metadata |

---

## Trust Boundaries

```
Internet
  │
  ▼
┌─────────────────────────────────┐
│   Sentinel Gateway (8080)       │  ← Trust boundary: all inbound requests are untrusted
│   WAF → Auth → RBAC → Proxy    │
└─────────────────────────────────┘
  │              │              │
  ▼              ▼              ▼
Keycloak     PostgreSQL       Redis
(OIDC)       (API keys,       (rate limits,
             policies)        nonces)
  │
  ▼
Downstream Services (internal network — trusted zone)
```

---

## Threat Scenarios (STRIDE)

### S — Spoofing

| # | Threat | Mitigation |
|---|--------|------------|
| S1 | Forged JWT (attacker generates a JWT signed with their own key) | RS256 signature verified against Keycloak JWKS; wrong key → 401 |
| S2 | JWT with fake issuer | `iss` claim validated against configured `sentinel.security.jwt.issuer` |
| S3 | Fake X-Tenant-Id header (tenant spoofing) | `JwtHeadersFilter` overwrites `X-Tenant-Id` with the JWT-derived value; header from client is discarded |
| S4 | API key brute force | Keys are random (`sgk_` + 32 hex bytes = 128 bits entropy); no enumeration endpoint |
| S5 | HMAC signature replay | Redis SET NX nonce store; replayed nonce → 401 within the TTL window (10 min) |
| S6 | Expired JWT reuse | `exp` claim validated on every request; 401 on expiry |

### T — Tampering

| # | Threat | Mitigation |
|---|--------|------------|
| T1 | JWT payload tampering (modify claims after signing) | RS256 signature covers the full header.payload; any modification → signature mismatch → 401 |
| T2 | HMAC request body tampering | Body SHA-256 hash is part of the HMAC input; tampered body → signature mismatch → 401 |
| T3 | SQL injection via path/query | `SqlInjectionDetector` (score=60 > block threshold=40) → 400 |
| T4 | XSS in query parameters | `XssDetector` (score=50 > block threshold=40) → 400 |
| T5 | Command injection | `CommandInjectionDetector` (score=80 > block threshold=40) → 400 |

### R — Repudiation

| # | Threat | Mitigation |
|---|--------|------------|
| R1 | Attacker denies making a request | Every request produces an `AuditEvent` with requestId, IP, method, path, user, outcome; events are shipped to Kafka for durable storage |
| R2 | Admin claims they didn't revoke an API key | Admin actions go through the JWT-authenticated `/admin/**` endpoints; audit log captures the admin's `X-User-Id` |

### I — Information Disclosure

| # | Threat | Mitigation |
|---|--------|------------|
| I1 | API key hash exposed in admin responses | `ApiKeyResponse` DTO explicitly excludes `keyHash`; raw key only returned at creation |
| I2 | Stack traces in error responses | Spring Boot `server.error.include-stacktrace=never` (default); gateway returns structured errors |
| I3 | Sensitive headers forwarded to downstream | `JwtHeadersFilter` propagates only `X-User-Id`, `X-Tenant-Id`, `X-User-Roles` from verified tokens |
| I4 | Path traversal reveals file structure | `PathTraversalDetector` score=30 logs but does not block (score below threshold); routing to `lb://` services limits exposure |

### D — Denial of Service

| # | Threat | Mitigation |
|---|--------|------------|
| D1 | Request flooding per client | Redis fixed-window rate limiter (ANONYMOUS: 100/min, USER: 1000/min, PREMIUM: 10000/min) → 429 |
| D2 | Daily/monthly quota exhaustion per tenant | Redis INCR+EXPIRE quota counters with per-tenant overrides → 429 |
| D3 | Slow downstream exhausting gateway threads | Global response timeout (30s) + per-route circuit breaker; slow upstream → 504/503 |
| D4 | Retry storms on upstream failures | Retries only on GET/HEAD 5xx (not on connection errors); CB opens after sustained failures |
| D5 | Large payload bypass WAF | Body scanning not yet implemented (future phase); URI-only WAF mitigates URL-borne attacks |

### E — Elevation of Privilege

| # | Threat | Mitigation |
|---|--------|------------|
| E1 | Regular user accessing `/admin/**` | `SecurityConfig` requires `ROLE_ADMIN` for all `/admin/**` paths; 403 otherwise |
| E2 | Forging ADMIN role in JWT | ADMIN role comes from Keycloak's `realm_access.roles`; JWT is RS256-signed by Keycloak — cannot be forged without the private key |
| E3 | Scope escalation (claiming scopes not granted) | `RouteAuthorizationFilter` checks `requiredScopes` from the verified JWT's scope/scp claim |
| E4 | Cross-tenant data access | Tenant context from JWT enforced by `TenantIsolationFilter`; cross-tenant requests on `tenantRequired` routes → 403 |

---

## Residual Risks

| Risk | Severity | Accepted Because |
|------|----------|------------------|
| No body scanning in WAF | MEDIUM | URI-based WAF catches common attacks; body scanning adds complexity and buffering overhead |
| JWT not checked for revocation | LOW | Tokens are short-lived (300s); revocation via Keycloak logout is handled at the OIDC layer |
| Path traversal not blocked at score=30 | LOW | Score below block threshold by design; logged; `lb://` URIs prevent filesystem exposure |
| Redis single-point-of-failure for rate limiting | LOW | Rate limiting fails open (disabled) on Redis unavailability; gateway continues serving traffic |

---

## Security Controls Summary

| Layer | Control | Implementation |
|-------|---------|----------------|
| Network | TLS termination | External (load balancer / ingress) — not in gateway scope |
| WAF | Pattern detection | `ThreatDetectionFilter` (HIGHEST_PRECEDENCE+2) |
| Authentication | JWT RS256 | Spring Security OAuth2 Resource Server + `ReactiveJwtAuthenticationConverter` |
| Authentication | API key | `ApiKeyAuthenticationFilter` + SHA-256 hash comparison |
| Authentication | HMAC | `HmacVerificationFilter` + constant-time MAC comparison + nonce store |
| Authorization | RBAC | `RouteAuthorizationFilter` + `RolePermissions` + `SecurityConfig.hasRole()` |
| Tenant isolation | Tenant context | `TenantIsolationFilter` + `JwtHeadersFilter` (header overwrite) |
| Rate limiting | Fixed-window | `RateLimitFilter` + Redis Lua script (atomic) |
| Quotas | Daily/monthly | `QuotaFilter` + Redis INCR+EXPIRE |
| Replay prevention | Nonce store | Redis SET NX EX; 10-minute window |
| Audit | Event stream | `AuditLoggingFilter` → `KafkaAuditEventPublisher` |
| Observability | Metrics + tracing | Micrometer → Prometheus; OTel bridge → structured logs with traceId |
