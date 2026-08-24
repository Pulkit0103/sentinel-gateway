package com.sentinelgateway.gateway.headercountdist;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for request header count distribution tracking (Phase 58).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.header-count")
public class HeaderCountProperties {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
