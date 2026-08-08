package com.sentinelgateway.gateway.security;

import java.util.List;
import java.util.Objects;

/**
 * Verified identity of the caller, extracted exclusively from the validated JWT.
 *
 * All fields come from claims that were cryptographically verified by Spring
 * Security before this object is constructed — callers cannot inject arbitrary
 * identity by sending spoofed headers.
 *
 * Upstream services receive this identity as trusted headers (X-User-Id,
 * X-Tenant-Id, X-User-Roles) added by {@link JwtHeadersFilter}. They must
 * accept these headers ONLY from the gateway, never from external callers.
 */
public record AuthenticatedPrincipal(
        String userId,
        String tenantId,
        List<String> roles,
        List<String> scopes,
        String clientId,
        AuthenticationType authenticationType
) {
    public AuthenticatedPrincipal {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(authenticationType, "authenticationType must not be null");
        roles = roles != null ? List.copyOf(roles) : List.of();
        scopes = scopes != null ? List.copyOf(scopes) : List.of();
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
