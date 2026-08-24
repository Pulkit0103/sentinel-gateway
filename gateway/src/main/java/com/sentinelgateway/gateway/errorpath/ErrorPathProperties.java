package com.sentinelgateway.gateway.errorpath;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for error-path tracking (Phase 56).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.error-path")
public class ErrorPathProperties {

    private boolean enabled = true;
    private int maxRecords = 200;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxRecords() { return maxRecords; }
    public void setMaxRecords(int maxRecords) { this.maxRecords = maxRecords; }
}
