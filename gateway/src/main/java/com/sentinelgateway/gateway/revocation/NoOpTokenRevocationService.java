package com.sentinelgateway.gateway.revocation;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * No-op implementation of {@link TokenRevocationService} used when Redis is unavailable
 * or revocation is disabled ({@code sentinel.revocation.enabled} is not {@code true}).
 *
 * Always reports tokens as not revoked — safe fallback that keeps the gateway
 * operational without Redis.
 */
public class NoOpTokenRevocationService implements TokenRevocationService {

    @Override
    public Mono<Void> revokeToken(String jti, Duration remainingTtl) {
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> isRevoked(String jti) {
        return Mono.just(false);
    }
}
