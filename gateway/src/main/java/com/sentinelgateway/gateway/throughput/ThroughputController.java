package com.sentinelgateway.gateway.throughput;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for request throughput rate statistics (Phase 66).
 */
@RestController
@RequestMapping("/admin/throughput-stats")
@PreAuthorize("hasRole('ADMIN')")
public class ThroughputController {

    private final ThroughputRegistry registry;
    private final ThroughputProperties properties;

    public ThroughputController(ThroughputRegistry registry, ThroughputProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "total", snap.get("total"),
                "peakRps", snap.get("peakRps"),
                "rps1s",  snap.get("rps1s"),
                "rps10s", snap.get("rps10s"),
                "rps60s", snap.get("rps60s")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "total", 0L);
    }
}
