package com.sentinelgateway.gateway.schemestat;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for URI scheme distribution statistics (Phase 61).
 */
@RestController
@RequestMapping("/admin/scheme-stats")
@PreAuthorize("hasRole('ADMIN')")
public class SchemeStatController {

    private final SchemeStatRegistry registry;
    private final SchemeStatProperties properties;

    public SchemeStatController(SchemeStatRegistry registry, SchemeStatProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "total", snap.get("total"),
                "schemes", snap.get("schemes")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "total", 0L);
    }
}
