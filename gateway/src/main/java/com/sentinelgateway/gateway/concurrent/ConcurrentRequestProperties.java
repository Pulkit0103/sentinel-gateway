package com.sentinelgateway.gateway.concurrent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the concurrent request monitor (Phase 41).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.concurrent-requests")
public class ConcurrentRequestProperties {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
