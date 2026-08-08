package com.sentinelgateway.gateway.signing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis-backed nonce store using SET NX (set if not exists).
 *
 * SET key value EX ttl NX — atomic: sets the key only if it doesn't exist,
 * with expiry in one command. Returns true if set (nonce is fresh), false if
 * key already exists (replay detected).
 */
@Component
@ConditionalOnProperty(name = "sentinel.request-signing.enabled", havingValue = "true")
public class RedisNonceStore implements NonceStore {

    private static final String NONCE_KEY_PREFIX = "nonce:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisNonceStore(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Boolean> recordIfAbsent(String nonce, long ttlSec) {
        String key = NONCE_KEY_PREFIX + nonce;
        return redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(ttlSec));
    }
}
