# ADR-007: Security Filter Chain Ordering

**Status:** Accepted  
**Date:** 2026-08-09

## Context

Sentinel Gateway uses two types of filters:
- **`WebFilter`** — runs before routing; intercepts every request regardless of whether a route exists
- **`GlobalFilter`** — runs as part of the routing pipeline; only executes when a route is matched

Security filters must execute in a specific order to avoid bypassing controls:
1. WAF checks must run before any authentication (to reject malicious inputs before wasting auth CPU)
2. Authentication must run before authorization (you can't check permissions without identity)
3. Tenant isolation must run after JWT identity is established
4. Rate limiting and quotas must run after authentication (to key counters per user, not per IP)

## Decision

### WebFilter order (order value → filter):

| Order | Filter | Responsibility |
|-------|--------|----------------|
| `HIGHEST_PRECEDENCE + 1` | `AuditLoggingFilter` | Wraps the entire chain; records outcome after response |
| `HIGHEST_PRECEDENCE + 2` | `ThreatDetectionFilter` | WAF pattern matching; blocks before any auth |
| `HIGHEST_PRECEDENCE + 5` | `ApiKeyAuthenticationFilter` | Sets `ApiKeyAuthentication` in `SecurityContext` |
| `HIGHEST_PRECEDENCE + 8` | `HmacVerificationFilter` | Verifies HMAC signature; sets `HmacAuthentication` |
| `0` | Spring Security | JWT validation; sets `JwtAuthenticationToken` |

### GlobalFilter order:

| Order | Filter | Responsibility |
|-------|--------|----------------|
| `+10` | `JwtHeadersFilter` | Propagates `X-User-Id`, `X-Tenant-Id`, `X-User-Roles` from verified JWT |
| `+20` | `RouteAuthorizationFilter` | Enforces `requiredScopes` per route |
| `+30` | `TenantIsolationFilter` | Enforces tenant context; blocks cross-tenant access on restricted routes |
| `+40` | `RateLimitFilter` | Redis fixed-window rate check per user/IP |
| `+50` | `QuotaFilter` | Redis daily/monthly tenant quota check |

## Rationale for Key Ordering Choices

**AuditLoggingFilter at HIGHEST_PRECEDENCE+1:** Must wrap the entire chain including Spring Security
to capture the final response status and outcome after all filters execute. Uses `Mono.defer` to
publish the audit event after `chain.filter(exchange)` completes.

**ThreatDetectionFilter at HIGHEST_PRECEDENCE+2:** Runs before authentication to reject SQL injection,
XSS, and command injection patterns without spending CPU on JWT validation for clearly malicious requests.

**ApiKeyAuthFilter before Spring Security (+5 vs 0):** The `ApiKeyAuthenticationFilter` sets a
custom `Authentication` object. If Spring Security runs first and the request has no Bearer token,
it would reject the request before the API key filter gets a chance.

**JwtHeadersFilter as GlobalFilter (+10):** Only runs for matched routes. Propagates JWT claims as
HTTP headers to the upstream service. Must run before RouteAuthorizationFilter so that scope/role
headers are set before the authorization check.

## Consequences

**Positive:**
- Each filter has a single, well-defined responsibility
- The ordering guarantees that no filter executes before its prerequisites
- Adding new filters is straightforward: choose an order value in the appropriate range

**Negative:**
- Order values are spread across `WebFilter` and `GlobalFilter` types; cannot directly compare
  order values across types (they are independent ordered lists)
- `AuditLoggingFilter` at HIGHEST_PRECEDENCE+1 means audit records are created even for
  requests blocked by `ThreatDetectionFilter` at +2 — this is intentional (blocked attempts ARE audited)
