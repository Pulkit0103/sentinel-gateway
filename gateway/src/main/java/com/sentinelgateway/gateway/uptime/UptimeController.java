package com.sentinelgateway.gateway.uptime;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin endpoint exposing gateway uptime and request statistics (Phase 30).
 *
 * GET  /admin/uptime        — startTime, uptimeSeconds, requestCount
 * POST /admin/uptime/reset  — resets request counter only (startTime is immutable)
 *
 * Requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin/uptime")
public class UptimeController {

    private final UptimeRegistry registry;

    public UptimeController(UptimeRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public Mono<Map<String, Object>> getUptime() {
        return Mono.just(Map.of(
                "startTime", registry.getStartTime().toString(),
                "uptimeSeconds", registry.uptimeSeconds(),
                "requestCount", registry.getRequestCount()
        ));
    }

    @PostMapping("/reset")
    public Mono<Map<String, Object>> resetCounters() {
        registry.reset();
        return Mono.just(Map.of(
                "reset", true,
                "requestCount", 0
        ));
    }
}
