package com.sentinelgateway.gateway.latency;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for per-route latency percentiles (Phase 38).
 */
@RestController
@RequestMapping("/admin/latency")
@PreAuthorize("hasRole('ADMIN')")
public class LatencyController {

    private final LatencyRegistry registry;
    private final LatencyProperties properties;

    public LatencyController(LatencyRegistry registry, LatencyProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getLatencies() {
        return Map.of(
                "enabled", properties.isEnabled(),
                "routeCount", registry.routeCount(),
                "routes", registry.snapshots()
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "remaining", registry.routeCount());
    }
}
