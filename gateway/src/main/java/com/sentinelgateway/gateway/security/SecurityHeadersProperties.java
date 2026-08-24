package com.sentinelgateway.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for response security headers (Phase 29).
 *
 * sentinel.security-headers:
 *   enabled: true
 *   headers:
 *     X-Content-Type-Options: nosniff
 *     X-Frame-Options: DENY
 *     Referrer-Policy: strict-origin-when-cross-origin
 *     X-XSS-Protection: "0"
 *     Permissions-Policy: interest-cohort=()
 *
 * All configured headers are force-set (override any value already present).
 * Setting a header value to blank removes it from the effective map so the
 * filter does not set it — any value written by Spring Security remains.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.security-headers")
public class SecurityHeadersProperties {

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>(Map.of(
            "X-Content-Type-Options", "nosniff",
            "X-Frame-Options", "DENY",
            "Referrer-Policy", "strict-origin-when-cross-origin",
            "X-XSS-Protection", "0",
            "Permissions-Policy", "interest-cohort=()"
    ));

    private boolean enabled = true;
    private Map<String, String> headers = new LinkedHashMap<>(DEFAULTS);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    /**
     * Returns configured headers with blank-value entries removed.
     * The caller (filter) force-sets all returned entries on the response.
     */
    public Map<String, String> effectiveHeaders() {
        Map<String, String> result = new LinkedHashMap<>(headers);
        result.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isBlank());
        return result;
    }
}
