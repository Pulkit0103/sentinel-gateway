package com.sentinelgateway.gateway.errorpath;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for error-path ring-buffer statistics (Phase 56).
 */
@RestController
@RequestMapping("/admin/error-paths")
@PreAuthorize("hasRole('ADMIN')")
public class ErrorPathController {

    private final ErrorPathRegistry registry;
    private final ErrorPathProperties properties;

    public ErrorPathController(ErrorPathRegistry registry, ErrorPathProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "maxRecords", properties.getMaxRecords(),
                "total4xx", snap.get("total4xx"),
                "total5xx", snap.get("total5xx"),
                "recentErrors", snap.get("recentErrors")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true);
    }
}
