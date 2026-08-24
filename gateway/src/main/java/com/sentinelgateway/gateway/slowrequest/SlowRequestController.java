package com.sentinelgateway.gateway.slowrequest;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin endpoint for viewing detected slow requests (Phase 33).
 *
 * GET  /admin/slow-requests         — recent slow requests (oldest → newest)
 * POST /admin/slow-requests/clear   — wipe the in-memory log
 *
 * Requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin/slow-requests")
public class SlowRequestController {

    private final SlowRequestProperties properties;
    private final SlowRequestRegistry registry;

    public SlowRequestController(SlowRequestProperties properties, SlowRequestRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    @GetMapping
    public Mono<Map<String, Object>> getSlowRequests() {
        return Mono.just(Map.of(
                "enabled", properties.isEnabled(),
                "thresholdMs", properties.getThresholdMs(),
                "total", registry.size(),
                "records", registry.all()
        ));
    }

    @PostMapping("/clear")
    public Mono<Map<String, Object>> clearLog() {
        int before = registry.size();
        registry.clear();
        return Mono.just(Map.of("cleared", before, "remaining", 0));
    }
}
