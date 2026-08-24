package com.sentinelgateway.gateway.headeraudit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration for response header audit tracking (Phase 51).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.header-audit")
public class HeaderAuditProperties {

    private boolean enabled = true;
    /** Headers whose presence/absence is tracked on every response. */
    private List<String> trackedHeaders = List.of(
            "Content-Security-Policy",
            "X-Content-Type-Options",
            "X-Frame-Options",
            "Strict-Transport-Security",
            "Cache-Control",
            "Referrer-Policy"
    );

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getTrackedHeaders() { return trackedHeaders; }
    public void setTrackedHeaders(List<String> trackedHeaders) { this.trackedHeaders = trackedHeaders; }
}
