# ADR-005: Redis for Distributed Rate Limiting and Nonce Store

**Status:** Accepted  
**Date:** 2026-08-09

## Context

The gateway runs as multiple replicas in production (2+ pods in Kubernetes). Rate limiting
must be enforced across all replicas — a per-JVM counter would allow each replica to accept
up to the full limit independently, effectively multiplying the actual throughput by the
replica count.

Similarly, HMAC nonce replay prevention requires atomic, TTL-based storage shared across
all gateway replicas.

## Decision

Use **Redis 7** for:
1. **Rate limiting** — fixed-window counters via atomic Lua script (`INCR` + `EXPIRE`)
2. **Quota enforcement** — daily/monthly tenant counters
3. **HMAC nonce store** — `SET NX EX` for atomic once-only nonce registration

All Redis operations are conditional on `sentinel.rate-limit.enabled`,
`sentinel.quota.enabled`, and `sentinel.request-signing.enabled` respectively.
This allows local development and CI without a Redis dependency.

## Implementation Detail

The rate limiter uses a Lua script to ensure the `INCR` and `EXPIRE` operations are atomic.
Without atomicity, a race condition between `INCR` and `EXPIRE` could leave a counter
without a TTL, causing it to never reset.

```lua
local count = redis.call('INCR', KEYS[1])
if count == 1 then
  redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return count
```

## Consequences

**Positive:**
- Sub-millisecond rate limit checks (Redis is in-memory)
- Atomic counters prevent race conditions across replicas
- TTL-based counters self-clean; no background job required
- Nonce store prevents HMAC replay attacks within the time window

**Negative:**
- Redis becomes a soft dependency; failure causes rate limiting to fail open
  (see FAILURE_MODES.md for detailed behavior)
- Redis single-node has no built-in HA; production requires Redis Sentinel or Cluster
- `GatewayRedisAutoConfiguration` must be excluded to prevent Spring Cloud Gateway from
  registering its own Redis rate limiter (which conflicts with our implementation)

## Alternatives Considered

- **In-memory Guava cache per replica** — rejected; not shared across replicas
- **PostgreSQL advisory locks** — rejected; too slow (DB round-trip per request)
- **Hazelcast** — rejected; adds significant operational complexity
