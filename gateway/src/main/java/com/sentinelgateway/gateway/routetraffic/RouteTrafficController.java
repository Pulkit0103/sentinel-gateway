package com.sentinelgateway.gateway.routetraffic;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for per-route windowed traffic statistics (Phase 53).
 */
@RestController
@RequestMapping("/admin/route-traffic")
@PreAuthorize("hasRole('ADMIN')")
public class RouteTrafficController {

    private final RouteTrafficRegistry registry;
    private final RouteTrafficProperties properties;

    public RouteTrafficController(RouteTrafficRegistry registry, RouteTrafficProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "routes", snap.get("routes")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true);
    }
}
