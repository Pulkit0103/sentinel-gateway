package com.sentinelgateway.gateway.revocation;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Service contract for JWT token revocation.
 *
 * Implementations maintain a blocklist of revoked JWT IDs (JTI claims).
 * The gateway checks this blocklist on every authenticated request to prevent
 * use of tokens that have been explicitly invalidated (e.g., on logout or
 * credential compromise) before their natural expiry.
 */
public interface TokenRevocationService {

    /**
     * Revoke a token by storing its JTI in the blocklist.
     *
     * @param jti          the JWT ID claim (unique identifier) of the token to revoke
     * @param remainingTtl how long to keep the entry — should equal the token's
     *                     remaining lifetime so the blocklist entry self-expires
     *                     when the token would have expired anyway
     */
    Mono<Void> revokeToken(String jti, Duration remainingTtl);

    /**
     * Check whether a token has been revoked.
     *
     * @param jti the JWT ID claim to check
     * @return {@code true} if the token is in the blocklist, {@code false} otherwise
     */
    Mono<Boolean> isRevoked(String jti);
}
