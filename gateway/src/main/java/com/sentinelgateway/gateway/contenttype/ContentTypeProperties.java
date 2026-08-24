package com.sentinelgateway.gateway.contenttype;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for response Content-Type distribution tracking (Phase 47).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.content-type-stats")
public class ContentTypeProperties {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
