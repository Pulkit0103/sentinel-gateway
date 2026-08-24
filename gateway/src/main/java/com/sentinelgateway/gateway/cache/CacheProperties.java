package com.sentinelgateway.gateway.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the per-route response caching subsystem.
 *
 * Bound from the {@code sentinel.cache} prefix in application.yml.
 *
 * When {@code enabled} is {@code false} (the default) the {@link ResponseCacheFilter}
 * bean is not registered, so no Redis access is attempted.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.cache")
public class CacheProperties {

    /**
     * Master switch for response caching.
     *
     * Default is {@code false} to avoid requiring a live Redis instance in
     * environments that don't have one (dev, CI, unit tests).
     * Set to {@code true} in Docker / Kubernetes where Redis is available.
     */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
