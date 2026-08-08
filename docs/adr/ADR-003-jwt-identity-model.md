# ADR-003 — JWT Identity Model

**Status:** Accepted  
**Date:** 2026-08-09  
**Phase:** 4 — JWT Identity Model

---

## Context

After Phase 3 established JWT-based authentication, Phase 4 introduces a
canonical identity domain model. Upstream services need caller identity
(user ID, tenant, roles, scopes) passed as trusted headers. The identity
must come exclusively from the verified JWT — never from caller-supplied
headers, which can be spoofed.

Three design questions arose:

1. **Where to extract identity?** In each filter individually, or via a
   centralized extractor?
2. **What format for roles?** Keycloak stores realm roles in
   `realm_access.roles` (nested object). Generic JWT providers use a flat
   `roles` array. Which to support?
3. **Should the identity object be in the reactive context or in exchange
   attributes?** Both are feasible; the choice affects testability and
   coupling.

---

## Decision

### `AuthenticatedPrincipal` — immutable value object

All identity facts are consolidated into a single `AuthenticatedPrincipal`
record with fields: `userId`, `tenantId`, `roles`, `scopes`, `clientId`,
`authenticationType`. The record enforces immutability via defensive copies
in the compact constructor.

### `JwtPrincipalExtractor` — single extraction point

A `@Component` class converts a Spring Security `Jwt` into an
`AuthenticatedPrincipal`. All claim-parsing logic lives here:
- Roles: tries `realm_access.roles` (Keycloak) then `roles` claim (generic)
- Scopes: tries `scope` string (space-separated) then `scp` list
- Client ID: tries `azp` (Keycloak `authorized_party`) then `client_id`

### Headers to upstream

`JwtHeadersFilter` uses `JwtPrincipalExtractor` and forwards:
- `X-User-Id` — JWT `sub`
- `X-Tenant-Id` — `tenant_id` custom claim (if present)
- `X-User-Roles` — comma-separated roles (if any)

### Identity in Reactor context (NOT exchange attributes)

Identity lives in Spring Security's `ReactiveSecurityContextHolder` (Reactor
context) and is extracted by `JwtHeadersFilter` when proxying. Exchange
attributes are not used — they would require explicit population and cleanup
while the security context is already managed by Spring Security.

---

## Consequences

**Positive:**
- Single extraction point → claim-parsing changes require one edit
- Upstream services see a consistent set of headers regardless of JWT format
- `AuthenticatedPrincipal` is testable in isolation without Spring context

**Negative:**
- Upstream services must strip these headers from any requests NOT routed
  through the gateway (i.e., direct access must be blocked at the network
  level)
- Adding a new claim source (API key, mTLS) in Phase 6/7 requires extending
  `AuthenticationType` and `JwtHeadersFilter`

**Neutral:**
- Supporting both `realm_access.roles` and flat `roles` makes the gateway
  compatible with Keycloak and non-Keycloak IdPs without configuration

---

## Alternatives Considered

### Alternative A: Parse claims inline in each filter
Rejected — duplicates parsing logic; subtle divergences are a maintenance risk.

### Alternative B: Store `AuthenticatedPrincipal` in exchange attributes
Rejected — requires an explicit pre-filter to populate attributes. Using the
`SecurityContext` (already populated by Spring Security) is simpler and avoids
a filter ordering dependency.
