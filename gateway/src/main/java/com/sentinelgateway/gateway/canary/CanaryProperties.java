package com.sentinelgateway.gateway.canary;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binds {@code sentinel.canary.routes.*} configuration into a map of
 * {@link CanaryConfig} objects keyed by route ID.
 *
 * <p>Example YAML:
 * <pre>
 * sentinel:
 *   canary:
 *     routes:
 *       my-service:
 *         canary-uri: http://my-service-v2:8080
 *         weight: 10   # 10 % of traffic goes to canary
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "sentinel.canary")
public class CanaryProperties {

    private Map<String, CanaryConfig> routes = new LinkedHashMap<>();

    public Map<String, CanaryConfig> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, CanaryConfig> routes) {
        this.routes = routes;
    }
}
