package com.sentinelgateway.gateway.cachettl;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for response Cache-Control TTL distribution tracking (Phase 57).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.cache-ttl")
public class CacheTtlProperties {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
