package com.sentinelgateway.gateway.refererstat;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for Referer domain distribution statistics (Phase 63).
 */
@RestController
@RequestMapping("/admin/referer-stats")
@PreAuthorize("hasRole('ADMIN')")
public class RefererStatController {

    private final RefererStatRegistry registry;
    private final RefererStatProperties properties;

    public RefererStatController(RefererStatRegistry registry, RefererStatProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot(properties.getTopN());
        return Map.of(
                "enabled", properties.isEnabled(),
                "total", snap.get("total"),
                "topDomains", snap.get("topDomains")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "total", 0L);
    }
}
