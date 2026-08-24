package com.sentinelgateway.gateway.responsesize;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for response body size tracking (Phase 42).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.response-size")
public class ResponseSizeProperties {

    private boolean enabled = true;
    private int maxRecords = 200;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxRecords() { return maxRecords; }
    public void setMaxRecords(int maxRecords) { this.maxRecords = maxRecords; }
}
