package com.sentinelgateway.gateway.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads route definitions from {@code sentinel.gateway.routes[*]} in application.yml
 * (or any externalized configuration source).
 *
 * Spring needs mutable JavaBean-style properties for list binding, so this class
 * uses setters. It converts to immutable {@link RouteDefinition} domain objects
 * via {@link #toRouteDefinitions()}.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.gateway")
public class RouteDefinitionProperties {

    private List<RouteEntry> routes = new ArrayList<>();

    public List<RouteEntry> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteEntry> routes) {
        this.routes = routes != null ? routes : new ArrayList<>();
    }

    public List<RouteDefinition> toRouteDefinitions() {
        return routes.stream()
                .map(RouteEntry::toRouteDefinition)
                .toList();
    }

    /** Mutable binding class for a single route entry in YAML. */
    public static class RouteEntry {
        private String routeId;
        private String path;
        private String serviceUri;
        private List<String> methods = new ArrayList<>();
        private boolean enabled = true;
        private List<String> requiredScopes = new ArrayList<>();
        private boolean tenantRequired = false;
        private String rateLimitPolicy = "DEFAULT";
        private int stripPrefix = 0;
        private Map<String, String> addRequestHeaders = new HashMap<>();
        private List<String> allowedIps = new ArrayList<>();
        private List<String> blockedIps = new ArrayList<>();

        public RouteDefinition toRouteDefinition() {
            return new RouteDefinition(
                    routeId, path, serviceUri, methods, enabled,
                    requiredScopes, tenantRequired, rateLimitPolicy,
                    stripPrefix, addRequestHeaders,
                    allowedIps, blockedIps);
        }

        // ── getters / setters ─────────────────────────────────────────────
        public String getRouteId()   { return routeId; }
        public void setRouteId(String v) { this.routeId = v; }

        public String getPath()      { return path; }
        public void setPath(String v) { this.path = v; }

        public String getServiceUri() { return serviceUri; }
        public void setServiceUri(String v) { this.serviceUri = v; }

        public List<String> getMethods() { return methods; }
        public void setMethods(List<String> v) { this.methods = v != null ? v : new ArrayList<>(); }

        public boolean isEnabled()   { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }

        public List<String> getRequiredScopes() { return requiredScopes; }
        public void setRequiredScopes(List<String> v) { this.requiredScopes = v != null ? v : new ArrayList<>(); }

        public boolean isTenantRequired() { return tenantRequired; }
        public void setTenantRequired(boolean v) { this.tenantRequired = v; }

        public String getRateLimitPolicy() { return rateLimitPolicy; }
        public void setRateLimitPolicy(String v) { this.rateLimitPolicy = v; }

        public int getStripPrefix() { return stripPrefix; }
        public void setStripPrefix(int v) { this.stripPrefix = v; }

        public Map<String, String> getAddRequestHeaders() { return addRequestHeaders; }
        public void setAddRequestHeaders(Map<String, String> v) { this.addRequestHeaders = v != null ? v : new HashMap<>(); }

        public List<String> getAllowedIps() { return allowedIps; }
        public void setAllowedIps(List<String> v) { this.allowedIps = v != null ? v : new ArrayList<>(); }

        public List<String> getBlockedIps() { return blockedIps; }
        public void setBlockedIps(List<String> v) { this.blockedIps = v != null ? v : new ArrayList<>(); }
    }
}
