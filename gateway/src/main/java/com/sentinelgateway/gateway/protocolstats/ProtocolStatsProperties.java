package com.sentinelgateway.gateway.protocolstats;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for request Accept header distribution tracking (Phase 52).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.accept-stats")
public class ProtocolStatsProperties {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
