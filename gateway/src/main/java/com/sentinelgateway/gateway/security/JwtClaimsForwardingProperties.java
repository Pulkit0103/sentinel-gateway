package com.sentinelgateway.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for forwarding arbitrary JWT claims as upstream request headers.
 *
 * <p>When {@code enabled} is {@code true}, each entry in {@code mappings} causes the
 * named JWT claim to be extracted from the authenticated token and forwarded as the
 * specified HTTP header to the upstream service.  Only string-valued claims are
 * forwarded; non-string claims (arrays, objects, numbers) are silently skipped.
 *
 * <p>Example YAML configuration:
 * <pre>
 * sentinel:
 *   jwt:
 *     claims-forwarding:
 *       enabled: true
 *       mappings:
 *         - claim: "email"
 *           header: "X-User-Email"
 *         - claim: "preferred_username"
 *           header: "X-Username"
 *         - claim: "department"
 *           header: "X-Department"
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "sentinel.jwt.claims-forwarding")
public class JwtClaimsForwardingProperties {

    /** Whether configurable claims forwarding is active. Defaults to {@code false} (opt-in). */
    private boolean enabled = false;

    /**
     * Ordered list of JWT claim → HTTP header mappings to forward.
     * Empty by default; only processed when {@link #enabled} is {@code true}.
     */
    private List<ClaimMapping> mappings = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<ClaimMapping> getMappings() { return mappings; }
    public void setMappings(List<ClaimMapping> mappings) { this.mappings = mappings; }

    /**
     * A single JWT claim → HTTP header mapping.
     */
    public static class ClaimMapping {

        /** The JWT claim name to read (e.g. {@code "email"}). */
        private String claim;

        /** The HTTP request header name to set on the upstream request (e.g. {@code "X-User-Email"}). */
        private String header;

        public String getClaim() { return claim; }
        public void setClaim(String claim) { this.claim = claim; }

        public String getHeader() { return header; }
        public void setHeader(String header) { this.header = header; }
    }
}
