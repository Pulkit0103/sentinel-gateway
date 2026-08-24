package com.sentinelgateway.gateway.ipaccess;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for IP-based access control (Phase 28).
 *
 * sentinel.ip-access:
 *   enabled: false
 *   mode: DENYLIST          # or ALLOWLIST
 *   global-list:
 *     - "10.0.0.1"
 *   routes:
 *     payment-service:
 *       - "192.168.1.100"
 *
 * In ALLOWLIST mode: only IPs in the list are permitted; all others are rejected (403).
 * In DENYLIST mode: IPs in the list are rejected (403); all others are permitted.
 *
 * Route-specific lists override the global list for that route.
 * IP matching is exact string comparison (dotted-decimal, no CIDR).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.ip-access")
public class IpAccessProperties {

    public enum Mode { ALLOWLIST, DENYLIST }

    private boolean enabled = false;
    private Mode mode = Mode.DENYLIST;
    private List<String> globalList = new ArrayList<>();
    private Map<String, List<String>> routes = new HashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public List<String> getGlobalList() { return globalList; }
    public void setGlobalList(List<String> globalList) { this.globalList = globalList; }

    public Map<String, List<String>> getRoutes() { return routes; }
    public void setRoutes(Map<String, List<String>> routes) { this.routes = routes; }

    /** Returns the effective IP list for a route: route-specific list if configured, else global list. */
    public List<String> effectiveListForRoute(String routeId) {
        if (routeId != null && routes.containsKey(routeId)) {
            return routes.get(routeId);
        }
        return globalList;
    }
}
