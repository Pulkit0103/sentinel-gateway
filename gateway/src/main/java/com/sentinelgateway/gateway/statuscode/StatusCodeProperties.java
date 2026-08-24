package com.sentinelgateway.gateway.statuscode;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for response status code distribution tracking (Phase 40).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.status-codes")
public class StatusCodeProperties {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
