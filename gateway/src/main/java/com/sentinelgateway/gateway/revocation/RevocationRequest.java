package com.sentinelgateway.gateway.revocation;

import java.time.Instant;

/**
 * Request body for the {@code POST /admin/revoke} endpoint.
 *
 * @param jti       the JWT ID claim of the token to revoke
 * @param expiresAt the token's expiry instant (ISO-8601); used to compute remaining TTL
 */
public record RevocationRequest(String jti, Instant expiresAt) {
}
