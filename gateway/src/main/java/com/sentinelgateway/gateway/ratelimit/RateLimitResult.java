package com.sentinelgateway.gateway.ratelimit;

/**
 * Result of a rate limit check.
 *
 * @param allowed        true if the request is within the allowed rate
 * @param limit          configured max requests per window
 * @param remaining      remaining requests in the current window
 * @param resetAfterSecs seconds until the window resets
 */
public record RateLimitResult(boolean allowed, long limit, long remaining, long resetAfterSecs) {

    public static RateLimitResult allowed(long limit, long remaining, long resetAfterSecs) {
        return new RateLimitResult(true, limit, remaining, resetAfterSecs);
    }

    public static RateLimitResult denied(long limit, long resetAfterSecs) {
        return new RateLimitResult(false, limit, 0, resetAfterSecs);
    }
}
