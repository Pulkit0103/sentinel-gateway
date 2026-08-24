package com.sentinelgateway.gateway.hopcount;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for hop count guard statistics (Phase 44).
 */
@RestController
@RequestMapping("/admin/hop-stats")
@PreAuthorize("hasRole('ADMIN')")
public class HopCountController {

    private final HopCountRegistry registry;
    private final HopCountProperties properties;

    public HopCountController(HopCountRegistry registry, HopCountProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "maxHops", properties.getMaxHops(),
                "totalProcessed", snap.get("totalProcessed"),
                "totalRejected", snap.get("totalRejected"),
                "distribution", snap.get("distribution")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true);
    }
}
