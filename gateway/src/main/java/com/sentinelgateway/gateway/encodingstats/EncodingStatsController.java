package com.sentinelgateway.gateway.encodingstats;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for request Content-Encoding distribution statistics (Phase 54).
 */
@RestController
@RequestMapping("/admin/encoding-stats")
@PreAuthorize("hasRole('ADMIN')")
public class EncodingStatsController {

    private final EncodingStatsRegistry registry;
    private final EncodingStatsProperties properties;

    public EncodingStatsController(EncodingStatsRegistry registry, EncodingStatsProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "total", snap.get("total"),
                "encodings", snap.get("encodings")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "total", 0L);
    }
}
