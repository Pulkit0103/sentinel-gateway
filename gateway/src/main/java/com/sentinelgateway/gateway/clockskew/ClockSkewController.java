package com.sentinelgateway.gateway.clockskew;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for clock-skew detection statistics (Phase 50).
 */
@RestController
@RequestMapping("/admin/clock-skew")
@PreAuthorize("hasRole('ADMIN')")
public class ClockSkewController {

    private final ClockSkewRegistry registry;
    private final ClockSkewProperties properties;

    public ClockSkewController(ClockSkewRegistry registry, ClockSkewProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "toleranceSeconds", properties.getToleranceSeconds(),
                "headerName", properties.getHeaderName(),
                "totalChecked", snap.get("totalChecked"),
                "totalSkewed", snap.get("totalSkewed"),
                "recentSkewed", snap.get("recentSkewed")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true);
    }
}
