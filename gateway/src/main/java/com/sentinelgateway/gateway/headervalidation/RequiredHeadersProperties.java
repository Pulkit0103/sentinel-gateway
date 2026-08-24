package com.sentinelgateway.gateway.headervalidation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-route required-header configuration.
 *
 * Example YAML:
 * <pre>
 * sentinel:
 *   required-headers:
 *     enabled: true
 *     routes:
 *       payment-service:
 *         - X-Idempotency-Key
 *         - X-Request-Id
 *       order-service:
 *         - X-Correlation-Id
 * </pre>
 *
 * When a route ID appears in {@code routes}, every incoming request to that route
 * must supply all listed headers; missing any one returns 400 Bad Request.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.required-headers")
public class RequiredHeadersProperties {

    private boolean enabled = false;

    /** Map of routeId → list of header names that must be present. */
    private Map<String, List<String>> routes = Map.of();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, List<String>> getRoutes() { return routes; }
    public void setRoutes(Map<String, List<String>> routes) { this.routes = routes; }

    /** Returns the required headers for a given route, or an empty list if none configured. */
    public List<String> requiredHeadersForRoute(String routeId) {
        if (routeId == null) return List.of();
        return routes.getOrDefault(routeId, List.of());
    }
}
