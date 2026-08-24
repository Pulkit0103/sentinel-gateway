package com.sentinelgateway.gateway.revocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis-backed JWT token revocation service.
 *
 * Key format: {@code sentinel:revoked:{jti}}
 *
 * When a token is revoked, its JTI is stored in Redis with a TTL equal to the
 * token's remaining lifetime. The entry self-expires when the token would have
 * expired naturally, keeping the blocklist compact.
 *
 * Active only when {@code sentinel.revocation.enabled=true}.
 */
public class RedisTokenRevocationService implements TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenRevocationService.class);
    private static final String KEY_PREFIX = "sentinel:revoked:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisTokenRevocationService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> revokeToken(String jti, Duration remainingTtl) {
        String key = KEY_PREFIX + jti;
        log.info("Revoking token JTI={} with TTL={}s", jti, remainingTtl.getSeconds());
        return redisTemplate.opsForValue().set(key, "1", remainingTtl).then();
    }

    @Override
    public Mono<Boolean> isRevoked(String jti) {
        String key = KEY_PREFIX + jti;
        return redisTemplate.hasKey(key);
    }
}
