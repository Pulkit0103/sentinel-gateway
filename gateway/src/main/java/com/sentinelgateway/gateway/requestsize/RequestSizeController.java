package com.sentinelgateway.gateway.requestsize;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for the request body-size guard (Phase 37).
 */
@RestController
@RequestMapping("/admin/request-sizes")
@PreAuthorize("hasRole('ADMIN')")
public class RequestSizeController {

    private final OversizedRequestRegistry registry;
    private final RequestSizeProperties properties;

    public RequestSizeController(OversizedRequestRegistry registry, RequestSizeProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getOversizedRequests() {
        List<OversizedRequestRecord> records = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "maxBodyBytes", properties.getMaxBodyBytes(),
                "count", records.size(),
                "records", records
        );
    }

    @PostMapping("/clear")
    public Map<String, Object> clear() {
        registry.clear();
        return Map.of("cleared", true, "remaining", registry.count());
    }
}
