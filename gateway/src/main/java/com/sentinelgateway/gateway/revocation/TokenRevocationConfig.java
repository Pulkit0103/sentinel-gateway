package com.sentinelgateway.gateway.revocation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Conditional configuration for JWT token revocation.
 *
 * When {@code sentinel.revocation.enabled=true}, registers a Redis-backed
 * {@link RedisTokenRevocationService}. Otherwise, falls back to the no-op
 * implementation that always reports tokens as not revoked.
 */
@Configuration
public class TokenRevocationConfig {

    /**
     * Redis-backed revocation service — active when sentinel.revocation.enabled=true.
     */
    @Bean
    @ConditionalOnProperty(name = "sentinel.revocation.enabled", havingValue = "true")
    public TokenRevocationService redisTokenRevocationService(
            ReactiveStringRedisTemplate redisTemplate) {
        return new RedisTokenRevocationService(redisTemplate);
    }

    /**
     * No-op fallback — registered when no other TokenRevocationService bean is present.
     */
    @Bean
    @ConditionalOnMissingBean(TokenRevocationService.class)
    public TokenRevocationService noOpTokenRevocationService() {
        return new NoOpTokenRevocationService();
    }
}
