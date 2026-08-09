# Sentinel Gateway — Failure Mode Reference

Documents how Sentinel Gateway behaves when each external dependency is unavailable.
The gateway is designed for graceful degradation: non-critical failures disable optional
features rather than taking the entire gateway down.

---

## PostgreSQL Unavailable

**Impact:** API key authentication **fails closed** — API key requests cannot be validated.

| Scenario | Behavior |
|----------|----------|
| PostgreSQL unreachable at startup | Gateway context fails to start (R2DBC connection pool initialization fails) |
| PostgreSQL goes down after startup | API key lookups return errors; gateway returns 500 for requests using API key auth |
| JWT-authenticated requests | **Unaffected** — JWT validation does not touch PostgreSQL |
| Admin endpoints | **Degraded** — admin API key operations (list/create/revoke) fail with 500 |
| Kubernetes readiness probe | `/actuator/health/readiness` includes `db` indicator → returns DOWN → pod removed from load balancer |

**Recovery:** When PostgreSQL recovers, the R2DBC connection pool reconnects automatically.
No gateway restart required.

**Mitigation in production:**
- Run PostgreSQL with streaming replication and automatic failover (e.g., Patroni, RDS Multi-AZ)
- Configure connection pool retry: `r2dbc.pool.max-idle-time=30m` and `r2dbc.pool.validation-query=SELECT 1`

---

## Redis Unavailable

**Impact:** Rate limiting and quota enforcement **fail open** (requests are allowed through).
HMAC nonce store also unavailable.

| Feature | Behavior when Redis is down |
|---------|----------------------------|
| Rate limiting | Disabled (fail-open); all requests pass without rate check |
| Quotas | Disabled (fail-open); all requests pass without quota check |
| HMAC nonce verification | Nonce uniqueness cannot be verified; HMAC requests **rejected** (fail-closed) if signing is enabled |
| API key auth | Unaffected — keys are in PostgreSQL, not Redis |
| JWT auth | Unaffected |
| Kubernetes readiness probe | Redis is **excluded** from the readiness health group by design (`redis.enabled=false` in management.health) — gateway remains ready |

**Why fail-open for rate limiting?** Rate limiting is a traffic management control, not a
security gate. An unprotected request is better than an outage. A dedicated DDoS mitigation
layer (CDN, WAF) should sit in front in production.

**Recovery:** Rate limiter and quota filter automatically re-enable when Redis reconnects.
Spring Data Redis uses connection pool with automatic reconnection.

**Mitigation in production:**
- Run Redis in cluster mode or with Sentinel for HA
- Set `spring.data.redis.timeout=1s` to fail fast on Redis unavailability
- Monitor `redis_connected_clients` and set alerts on connection drops

---

## Kafka Unavailable

**Impact:** Audit event delivery **fails open** — requests are served but audit events are lost.

| Scenario | Behavior |
|----------|----------|
| Kafka unreachable | `KafkaAuditEventPublisher.publish()` logs a WARN and discards the event |
| Kafka broker failure mid-stream | Producer retries up to 3 times then discards (fire-and-forget configuration) |
| `sentinel.audit.kafka.enabled=false` | Falls back to `LoggingAuditEventPublisher` — events written to application logs only |
| Kubernetes readiness probe | Kafka is NOT in the readiness health group — gateway remains ready |

**Why fire-and-forget for audit?** The audit path must never block the request path.
A synchronous audit write would add latency and couple gateway availability to Kafka.
For compliance requirements, move to transactional outbox pattern (write to PostgreSQL
first, then stream to Kafka).

**Recovery:** When Kafka recovers, new events flow immediately. Events lost during the
outage are unrecoverable with the current implementation.

**Mitigation in production:**
- Run Kafka with 3+ brokers and replication factor ≥ 2
- Consider transactional outbox for compliance-critical audit requirements
- Monitor `sentinel.audit.events.discarded` (if counter added) or Kafka consumer lag

---

## Downstream Service Unavailable

**Impact:** Requests to the affected route return 503 or 504. Other routes are unaffected.

| Failure Mode | Behavior | HTTP Status |
|-------------|----------|-------------|
| Downstream unreachable (connection refused) | Circuit breaker counts connection error | 503 (CB open) or 500 |
| Downstream slow (>30s response timeout) | Gateway returns timeout after response-timeout | 504 Gateway Timeout |
| Downstream returns 5xx (GET/HEAD) | Retry up to 2 times, then propagate | 5xx (after retries) |
| Downstream returns 5xx (POST/PUT/DELETE) | Not retried (not safe); propagate immediately | 5xx |
| Circuit breaker OPEN | Short-circuit; no upstream call | 503 Service Unavailable |
| Circuit breaker HALF_OPEN | Probe request sent; 3 probes to close | 503 (if probe fails) |

**Circuit breaker settings (default):**
- Sliding window: 20 calls (COUNT_BASED)
- Opens when ≥ 50% of last 20 calls fail
- Half-open after 30 seconds
- Closes after 3 successful probe calls

**Per-route override:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      payment-service-cb:
        failureRateThreshold: 30   # stricter for payments
        waitDurationInOpenState: 60000
```

**Recovery:** Circuit breaker transitions to HALF_OPEN automatically. No operator intervention
required unless the downstream is permanently unavailable.

---

## Keycloak Unavailable

**Impact:** New JWT validation **fails** — tokens cannot be validated because JWKS cannot be fetched.

| Scenario | Behavior |
|----------|----------|
| Keycloak unreachable, JWKS never fetched | Spring Security cannot initialize; gateway startup MAY fail depending on eager JWKS fetch |
| Keycloak goes down after startup | Existing cached JWKS keys continue to work (Spring Security caches the JWK set) |
| JWKS cache TTL expires while Keycloak is down | New token validation attempts fail → 401 |
| API key auth | **Unaffected** — no Keycloak dependency |

**JWKS caching:** Spring Security's `NimbusReactiveJwtDecoder` caches the JWK set in memory.
The cache duration is controlled by the `max-age` response header from Keycloak (default: varies).
Configure a local cache TTL: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` with
`spring.cache.*` or use a custom `ReactiveJwtDecoder` with explicit cache TTL.

**Recovery:** JWKS cache refreshes automatically when Keycloak recovers.

**Mitigation in production:**
- Run Keycloak in cluster mode with PostgreSQL backend
- Pre-warm the JWKS cache at startup
- Consider API key as fallback auth mechanism for M2M services

---

## Summary Matrix

| Dependency | Rate Limiting | JWT Auth | API Key Auth | HMAC Auth | Audit Logging | Admin API |
|-----------|--------------|----------|--------------|-----------|---------------|-----------|
| PostgreSQL DOWN | Normal | Normal | **Failed (500)** | Normal | Normal | **Degraded** |
| Redis DOWN | **Open (no limit)** | Normal | Normal | **Rejected** | Normal | Normal |
| Kafka DOWN | Normal | Normal | Normal | Normal | **Lost (warn)** | Normal |
| Keycloak DOWN | Normal | **Failed (401)** | Normal | Normal | Normal | **Failed (JWT)** |
| Downstream DOWN | Normal | Normal | Normal | Normal | Normal | Normal |

**Failed** = requests fail for affected auth mechanism; other mechanisms work.
**Open** = feature disabled, not blocked.
**Lost** = events not durably stored.
**Degraded** = some operations fail, others succeed.
