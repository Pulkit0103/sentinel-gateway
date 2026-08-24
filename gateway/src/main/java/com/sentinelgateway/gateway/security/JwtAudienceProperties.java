package com.sentinelgateway.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configures required JWT audience validation.
 *
 * When {@code requiredAudiences} is non-empty, every JWT bearer request must
 * include at least one of the configured audience values in its {@code aud} claim.
 * Requests with missing or non-matching {@code aud} are rejected with 401.
 *
 * API-key authenticated requests and unauthenticated paths are unaffected.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.security.jwt")
public class JwtAudienceProperties {

    /**
     * JWT issuer — existing field, kept here for co-location.
     * Bind target: sentinel.security.jwt.issuer (also bound in SecurityConfig via @Value).
     */
    private String issuer = "";

    /**
     * Required audience values. If empty, audience is not validated.
     * At least one value in the JWT {@code aud} claim must be present in this list.
     */
    private List<String> requiredAudiences = List.of();

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public List<String> getRequiredAudiences() { return requiredAudiences; }
    public void setRequiredAudiences(List<String> requiredAudiences) {
        this.requiredAudiences = requiredAudiences;
    }

    public boolean isAudienceValidationEnabled() {
        return !requiredAudiences.isEmpty();
    }
}
