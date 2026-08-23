package com.sentinelgateway.gateway.signing;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

/**
 * Spring Security Authentication token for HMAC-signed requests.
 * The principal is the X-Client-Id from the signed request.
 */
public class HmacAuthentication extends AbstractAuthenticationToken {

    private final String clientId;

    public HmacAuthentication(String clientId) {
        super(List.of());
        this.clientId = clientId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getPrincipal() {
        return clientId;
    }
}
