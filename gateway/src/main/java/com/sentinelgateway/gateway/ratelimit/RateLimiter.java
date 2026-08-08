package com.sentinelgateway.gateway.ratelimit;

import reactor.core.publisher.Mono;

/**
 * Contract for distributed rate limiting.
 *
 * Implementations must be thread-safe and may be backed by Redis
 * (production) or an in-memory store (tests/dev).
 *
 * @param key    a stable identifier for the caller (userId, clientId, or IP)
 * @param policy the rate limit tier to apply
 */
public interface RateLimiter {

    Mono<RateLimitResult> checkAndIncrement(String key, RateLimitPolicy policy);
}
