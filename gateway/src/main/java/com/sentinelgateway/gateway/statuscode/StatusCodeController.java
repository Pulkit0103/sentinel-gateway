package com.sentinelgateway.gateway.statuscode;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for response status code distribution (Phase 40).
 */
@RestController
@RequestMapping("/admin/status-codes")
@PreAuthorize("hasRole('ADMIN')")
public class StatusCodeController {

    private final StatusCodeRegistry registry;
    private final StatusCodeProperties properties;

    public StatusCodeController(StatusCodeRegistry registry, StatusCodeProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStatusCodes() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "total", snap.get("total"),
                "buckets", snap.get("buckets"),
                "codes", snap.get("codes")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true);
    }
}
