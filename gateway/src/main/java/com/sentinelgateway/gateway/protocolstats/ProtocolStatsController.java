package com.sentinelgateway.gateway.protocolstats;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for HTTP protocol version distribution statistics (Phase 52).
 */
@RestController
@RequestMapping("/admin/protocol-stats")
@PreAuthorize("hasRole('ADMIN')")
public class ProtocolStatsController {

    private final ProtocolStatsRegistry registry;
    private final ProtocolStatsProperties properties;

    public ProtocolStatsController(ProtocolStatsRegistry registry, ProtocolStatsProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "total", snap.get("total"),
                "versions", snap.get("versions")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "total", 0L);
    }
}
