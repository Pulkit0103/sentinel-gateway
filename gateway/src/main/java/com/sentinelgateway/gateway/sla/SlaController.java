package com.sentinelgateway.gateway.sla;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin endpoint for SLA tracking data.
 *
 * GET  /admin/sla         — per-route SLA snapshots (totalRequests, breachCount, etc.)
 * POST /admin/sla/reset   — clear all SLA statistics
 *
 * All endpoints require ROLE_ADMIN (enforced by SecurityWebFilterChain).
 */
@RestController
@RequestMapping("/admin/sla")
public class SlaController {

    private final SlaTracker tracker;
    private final SlaProperties properties;

    public SlaController(SlaTracker tracker, SlaProperties properties) {
        this.tracker = tracker;
        this.properties = properties;
    }

    @GetMapping
    public Mono<Map<String, Object>> getSlaReport() {
        return Mono.just(Map.of(
                "enabled", properties.isEnabled(),
                "configuredRoutes", properties.getRoutes(),
                "statistics", tracker.snapshots()
        ));
    }

    @PostMapping("/reset")
    public Mono<Map<String, Object>> resetSla() {
        tracker.reset();
        return Mono.just(Map.of("reset", true));
    }
}
