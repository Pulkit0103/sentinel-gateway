package com.sentinelgateway.gateway.useragent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for request User-Agent distribution tracking (Phase 48).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.user-agent-stats")
public class UserAgentProperties {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
