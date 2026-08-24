package com.sentinelgateway.gateway.metrics;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin endpoint exposing real-time gateway request metrics.
 *
 * GET  /admin/request-metrics         — inflight, total completed, per-route counts
 * POST /admin/request-metrics/reset   — zero all counters (useful for load-test baselining)
 *
 * All endpoints require ROLE_ADMIN (enforced by SecurityWebFilterChain).
 */
@RestController
@RequestMapping("/admin/request-metrics")
public class RequestMetricsController {

    private final RequestMetricsRegistry registry;

    public RequestMetricsController(RequestMetricsRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public Mono<Map<String, Object>> getMetrics() {
        return Mono.just(Map.of(
                "inflightRequests", registry.getInflightRequests(),
                "totalCompleted", registry.getTotalCompleted(),
                "perRouteCompleted", registry.getPerRouteCompleted()
        ));
    }

    @PostMapping("/reset")
    public Mono<Map<String, Object>> resetMetrics() {
        registry.reset();
        return Mono.just(Map.of("reset", true));
    }
}
