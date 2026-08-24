package com.sentinelgateway.gateway.responseheadersize;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for response header size distribution tracking (Phase 59).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.response-header-size")
public class ResponseHeaderSizeProperties {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
