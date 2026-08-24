package com.sentinelgateway.gateway.errorrate;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin endpoint for viewing HTTP error rates per route (Phase 35).
 *
 * GET  /admin/error-rates        — per-route {total, errors, errorRatePct}
 * POST /admin/error-rates/reset  — clear all counters
 *
 * Requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin/error-rates")
public class ErrorRateController {

    private final ErrorRateRegistry registry;

    public ErrorRateController(ErrorRateRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public Mono<Map<String, Object>> getErrorRates() {
        return Mono.just(Map.of(
                "routeCount", registry.routeCount(),
                "routes", registry.snapshots()
        ));
    }

    @PostMapping("/reset")
    public Mono<Map<String, Object>> reset() {
        int before = registry.routeCount();
        registry.reset();
        return Mono.just(Map.of("cleared", before, "remaining", 0));
    }
}
