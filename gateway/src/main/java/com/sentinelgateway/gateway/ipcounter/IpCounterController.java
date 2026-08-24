package com.sentinelgateway.gateway.ipcounter;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for per-IP request count statistics (Phase 49).
 */
@RestController
@RequestMapping("/admin/ip-stats")
@PreAuthorize("hasRole('ADMIN')")
public class IpCounterController {

    private final IpCounterRegistry registry;
    private final IpCounterProperties properties;

    public IpCounterController(IpCounterRegistry registry, IpCounterProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot(properties.getTopN());
        return Map.of(
                "enabled", properties.isEnabled(),
                "topN", properties.getTopN(),
                "total", snap.get("total"),
                "uniqueIps", snap.get("uniqueIps"),
                "topIps", snap.get("topIps")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "total", 0L);
    }
}
