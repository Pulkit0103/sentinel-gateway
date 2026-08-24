package com.sentinelgateway.gateway.cachettl;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for response Cache-Control TTL distribution statistics (Phase 57).
 */
@RestController
@RequestMapping("/admin/cache-ttl-stats")
@PreAuthorize("hasRole('ADMIN')")
public class CacheTtlController {

    private final CacheTtlRegistry registry;
    private final CacheTtlProperties properties;

    public CacheTtlController(CacheTtlRegistry registry, CacheTtlProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "total", snap.get("total"),
                "buckets", snap.get("buckets")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "total", 0L);
    }
}
