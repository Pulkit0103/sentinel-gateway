package com.sentinelgateway.gateway.apikey;

import com.sentinelgateway.gateway.security.AuthenticatedPrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Spring Security Authentication token for API key-authenticated requests.
 * The principal is an AuthenticatedPrincipal built from the stored ApiKey metadata.
 */
public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final AuthenticatedPrincipal principal;

    public ApiKeyAuthentication(AuthenticatedPrincipal principal) {
        super(principal.roles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null; // raw key is never stored
    }

    @Override
    public AuthenticatedPrincipal getPrincipal() {
        return principal;
    }
}
