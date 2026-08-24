package com.sentinelgateway.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Per-route JWT claim authorization requirements.
 *
 * Example:
 * <pre>
 * sentinel:
 *   claim-authorization:
 *     enabled: true
 *     routes:
 *       payment-service:
 *         - claim: department
 *           allowedValues: [finance, engineering]
 *       internal-api:
 *         - claim: account_type
 *           allowedValues: [internal]
 * </pre>
 *
 * Each route can have multiple claim requirements; ALL must be satisfied (AND logic).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.claim-authorization")
public class ClaimAuthorizationProperties {

    private boolean enabled = false;

    /** Map of routeId → list of ClaimRequirement. All requirements must match (AND). */
    private Map<String, List<ClaimRequirement>> routes = Map.of();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, List<ClaimRequirement>> getRoutes() { return routes; }
    public void setRoutes(Map<String, List<ClaimRequirement>> routes) { this.routes = routes; }

    public List<ClaimRequirement> requirementsForRoute(String routeId) {
        if (routeId == null) return List.of();
        return routes.getOrDefault(routeId, List.of());
    }

    public static class ClaimRequirement {
        private String claim;
        private List<String> allowedValues = List.of();

        public String getClaim() { return claim; }
        public void setClaim(String claim) { this.claim = claim; }

        public List<String> getAllowedValues() { return allowedValues; }
        public void setAllowedValues(List<String> allowedValues) { this.allowedValues = allowedValues; }
    }
}
