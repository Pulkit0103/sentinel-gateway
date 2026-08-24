package com.sentinelgateway.gateway.latency;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for per-route response latency tracking (Phase 38).
 *
 * sentinel.latency:
 *   enabled: true
 *   max-samples-per-route: 1000   # ring-buffer depth per route for percentile computation
 */
@Component
@ConfigurationProperties(prefix = "sentinel.latency")
public class LatencyProperties {

    private boolean enabled = true;
    private int maxSamplesPerRoute = 1000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxSamplesPerRoute() { return maxSamplesPerRoute; }
    public void setMaxSamplesPerRoute(int maxSamplesPerRoute) { this.maxSamplesPerRoute = maxSamplesPerRoute; }
}
