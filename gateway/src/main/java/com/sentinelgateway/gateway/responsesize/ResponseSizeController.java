package com.sentinelgateway.gateway.responsesize;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for response body size monitoring (Phase 42).
 */
@RestController
@RequestMapping("/admin/response-sizes")
@PreAuthorize("hasRole('ADMIN')")
public class ResponseSizeController {

    private final ResponseSizeRegistry registry;
    private final ResponseSizeProperties properties;

    public ResponseSizeController(ResponseSizeRegistry registry, ResponseSizeProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        return Map.of(
                "enabled", properties.isEnabled(),
                "sampleCount", registry.getSampleCount(),
                "totalBytes", registry.getTotalBytes(),
                "averageBytes", registry.getAverageBytes(),
                "recentSamples", registry.recentSamples()
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "sampleCount", registry.getSampleCount());
    }
}
