package com.sentinelgateway.gateway.encodingstats;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for request encoding distribution tracking (Phase 54).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.encoding-stats")
public class EncodingStatsProperties {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
