package com.sentinelgateway.gateway.sla;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Per-route response time SLA targets (in milliseconds).
 *
 * Example:
 * <pre>
 * sentinel:
 *   sla:
 *     enabled: true
 *     routes:
 *       payment-service: 500   # must respond within 500ms
 *       order-service: 1000
 * </pre>
 *
 * Routes without an entry have no SLA tracked.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.sla")
public class SlaProperties {

    private boolean enabled = false;

    /** Map of routeId → target response time in milliseconds. */
    private Map<String, Long> routes = Map.of();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, Long> getRoutes() { return routes; }
    public void setRoutes(Map<String, Long> routes) { this.routes = routes; }

    public boolean hasSla(String routeId) {
        return routeId != null && routes.containsKey(routeId);
    }

    public long targetMs(String routeId) {
        return routes.getOrDefault(routeId, Long.MAX_VALUE);
    }
}
