package com.sentinelgateway.gateway.routetraffic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for per-route windowed traffic counting (Phase 53).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.route-traffic")
public class RouteTrafficProperties {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
