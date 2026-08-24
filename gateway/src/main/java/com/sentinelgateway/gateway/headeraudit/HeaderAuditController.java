package com.sentinelgateway.gateway.headeraudit;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for response header audit statistics (Phase 51).
 */
@RestController
@RequestMapping("/admin/header-audit")
@PreAuthorize("hasRole('ADMIN')")
public class HeaderAuditController {

    private final HeaderAuditRegistry registry;
    private final HeaderAuditProperties properties;

    public HeaderAuditController(HeaderAuditRegistry registry, HeaderAuditProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "trackedHeaders", properties.getTrackedHeaders(),
                "totalResponses", snap.get("totalResponses"),
                "coveragePercent", snap.get("coveragePercent"),
                "headers", snap.get("headers")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true);
    }
}
