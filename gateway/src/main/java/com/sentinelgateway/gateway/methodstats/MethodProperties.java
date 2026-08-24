package com.sentinelgateway.gateway.methodstats;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for HTTP method distribution tracking (Phase 46).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.method-stats")
public class MethodProperties {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
