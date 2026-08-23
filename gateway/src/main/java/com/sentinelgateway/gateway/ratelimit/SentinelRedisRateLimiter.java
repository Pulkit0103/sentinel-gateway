package com.sentinelgateway.gateway.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Fixed-window rate limiter backed by Redis.
 *
 * Algorithm (atomic via Lua script):
 *   1. INCR rl:{key}:{minute}
 *   2. On first increment (TTL == -1), SET TTL to 60 seconds
 *   3. Return [count, ttl]
 *
 * The Lua script executes atomically — INCR + conditional EXPIRE as a single
 * Redis command, preventing race conditions under concurrent load.
 *
 * Bean name is explicit ("sentinelRateLimiter") to avoid collision with
 * Spring Cloud Gateway's built-in GatewayRedisAutoConfiguration redisRateLimiter.
 *
 * Key format: rl:{key}:{epoch_minute}
 */
@Component("sentinelRateLimiter")
@ConditionalOnProperty(name = "sentinel.rate-limit.enabled", havingValue = "true")
public class SentinelRedisRateLimiter implements RateLimiter {

    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local count = redis.call('INCR', key)
            if count == 1 then
                redis.call('EXPIRE', key, 60)
            end
            local ttl = redis.call('TTL', key)
            return {count, ttl}
            """;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final RedisScript<List<Long>> script;

    public SentinelRedisRateLimiter(ReactiveStringRedisTemplate redisTemplate,
                                     RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.script = RedisScript.of(LUA_SCRIPT, (Class<List<Long>>) (Class<?>) List.class);
    }

    @Override
    public Mono<RateLimitResult> checkAndIncrement(String key, RateLimitPolicy policy) {
        long limit = properties.limitFor(policy);
        long minute = Instant.now().getEpochSecond() / 60;
        String redisKey = "rl:" + key + ":" + minute;

        return redisTemplate.execute(script, List.of(redisKey), List.of(String.valueOf(limit)))
                .next()
                .map(result -> {
                    long count = result.get(0);
                    long ttl = result.get(1);
                    long resetAfter = ttl > 0 ? ttl : 60;
                    if (count > limit) {
                        return RateLimitResult.denied(limit, resetAfter);
                    }
                    return RateLimitResult.allowed(limit, limit - count, resetAfter);
                });
    }
}
