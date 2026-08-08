package com.sentinelgateway.gateway.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Extracts an {@link AuthenticatedPrincipal} from a validated {@link Jwt}.
 *
 * Supports both Keycloak-style tokens (realm_access.roles) and generic JWT
 * tokens (roles claim, scope claim).
 *
 * Called only after Spring Security has verified the JWT's signature, expiry,
 * and issuer. The extracted principal is therefore trustworthy.
 */
@Component
public class JwtPrincipalExtractor {

    @SuppressWarnings("unchecked")
    public AuthenticatedPrincipal extract(Jwt jwt) {
        String userId = jwt.getSubject();
        String tenantId = jwt.getClaimAsString("tenant_id");

        // Roles: Keycloak stores realm roles under realm_access.roles
        List<String> roles;
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> rawRoles) {
            roles = (List<String>) rawRoles;
        } else if (jwt.hasClaim("roles")) {
            roles = Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElse(List.of());
        } else {
            roles = List.of();
        }

        // Scopes: space-separated "scope" string or list "scp"
        List<String> scopes;
        String scopeString = jwt.getClaimAsString("scope");
        if (scopeString != null && !scopeString.isBlank()) {
            scopes = Arrays.asList(scopeString.split("\\s+"));
        } else {
            scopes = Optional.ofNullable(jwt.getClaimAsStringList("scp")).orElse(List.of());
        }

        // Client ID: Keycloak uses "azp" (authorized party), generic uses "client_id"
        String clientId = jwt.getClaimAsString("azp");
        if (clientId == null) {
            clientId = jwt.getClaimAsString("client_id");
        }

        return new AuthenticatedPrincipal(userId, tenantId, roles, scopes, clientId, AuthenticationType.JWT);
    }
}
