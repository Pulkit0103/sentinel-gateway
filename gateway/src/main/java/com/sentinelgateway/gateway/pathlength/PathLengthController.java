package com.sentinelgateway.gateway.pathlength;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for path/query length rejection history (Phase 43).
 */
@RestController
@RequestMapping("/admin/path-rejections")
@PreAuthorize("hasRole('ADMIN')")
public class PathLengthController {

    private final PathLengthRegistry registry;
    private final PathLengthProperties properties;

    public PathLengthController(PathLengthRegistry registry, PathLengthProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getRejections() {
        return Map.of(
                "enabled", properties.isEnabled(),
                "maxPathLength", properties.getMaxPathLength(),
                "maxQueryLength", properties.getMaxQueryLength(),
                "count", registry.count(),
                "records", registry.snapshot()
        );
    }

    @PostMapping("/clear")
    public Map<String, Object> clear() {
        registry.clear();
        return Map.of("cleared", true, "remaining", registry.count());
    }
}
