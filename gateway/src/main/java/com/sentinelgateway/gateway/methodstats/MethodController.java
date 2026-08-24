package com.sentinelgateway.gateway.methodstats;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for HTTP method distribution statistics (Phase 46).
 */
@RestController
@RequestMapping("/admin/method-stats")
@PreAuthorize("hasRole('ADMIN')")
public class MethodController {

    private final MethodRegistry registry;
    private final MethodProperties properties;

    public MethodController(MethodRegistry registry, MethodProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "total", snap.get("total"),
                "methods", snap.get("methods")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "total", 0L);
    }
}
