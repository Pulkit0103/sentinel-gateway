package com.sentinelgateway.gateway.queryparam;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for query-parameter count distribution statistics (Phase 60).
 */
@RestController
@RequestMapping("/admin/query-param-stats")
@PreAuthorize("hasRole('ADMIN')")
public class QueryParamController {

    private final QueryParamRegistry registry;
    private final QueryParamProperties properties;

    public QueryParamController(QueryParamRegistry registry, QueryParamProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "total", snap.get("total"),
                "maxSeen", snap.get("maxSeen"),
                "buckets", snap.get("buckets")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "total", 0L);
    }
}
