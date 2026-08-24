package com.sentinelgateway.gateway.pathdepth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for URL path-depth distribution statistics (Phase 62).
 */
@RestController
@RequestMapping("/admin/path-depth-stats")
@PreAuthorize("hasRole('ADMIN')")
public class PathDepthController {

    private final PathDepthRegistry registry;
    private final PathDepthProperties properties;

    public PathDepthController(PathDepthRegistry registry, PathDepthProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "total", snap.get("total"),
                "maxSeen", snap.get("maxSeen"),
                "buckets", snap.get("buckets")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "total", 0L);
    }
}
