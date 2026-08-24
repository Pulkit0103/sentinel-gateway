package com.sentinelgateway.gateway.concurrent;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for the concurrent request monitor (Phase 41).
 */
@RestController
@RequestMapping("/admin/concurrent-requests")
@PreAuthorize("hasRole('ADMIN')")
public class ConcurrentRequestController {

    private final ConcurrentRequestRegistry registry;
    private final ConcurrentRequestProperties properties;

    public ConcurrentRequestController(ConcurrentRequestRegistry registry,
                                       ConcurrentRequestProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        return Map.of(
                "enabled", properties.isEnabled(),
                "current", registry.getCurrent(),
                "peak", registry.getPeak(),
                "totalCompleted", registry.getTotalCompleted()
        );
    }

    @PostMapping("/reset-peak")
    public Map<String, Object> resetPeak() {
        registry.resetPeak();
        return Map.of("peak", registry.getPeak(), "totalCompleted", registry.getTotalCompleted());
    }
}
