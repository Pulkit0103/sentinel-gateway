package com.sentinelgateway.gateway.routeerrorrate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for per-route error rate statistics (Phase 65).
 */
@RestController
@RequestMapping("/admin/route-error-rate")
@PreAuthorize("hasRole('ADMIN')")
public class RouteErrorRateController {

    private final RouteErrorRateRegistry registry;
    private final RouteErrorRateProperties properties;

    public RouteErrorRateController(RouteErrorRateRegistry registry, RouteErrorRateProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        return Map.of(
                "enabled", properties.isEnabled(),
                "routeCount", registry.routeCount(),
                "routes", registry.snapshot()
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "routeCount", 0);
    }
}
