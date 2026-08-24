package com.sentinelgateway.gateway.responseheadersize;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for response header size distribution statistics (Phase 59).
 */
@RestController
@RequestMapping("/admin/response-header-size-stats")
@PreAuthorize("hasRole('ADMIN')")
public class ResponseHeaderSizeController {

    private final ResponseHeaderSizeRegistry registry;
    private final ResponseHeaderSizeProperties properties;

    public ResponseHeaderSizeController(ResponseHeaderSizeRegistry registry, ResponseHeaderSizeProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "total", snap.get("total"),
                "maxSeenBytes", snap.get("maxSeenBytes"),
                "buckets", snap.get("buckets")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "total", 0L);
    }
}
