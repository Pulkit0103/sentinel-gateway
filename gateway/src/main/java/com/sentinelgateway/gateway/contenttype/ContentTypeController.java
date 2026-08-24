package com.sentinelgateway.gateway.contenttype;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for response Content-Type distribution statistics (Phase 47).
 */
@RestController
@RequestMapping("/admin/content-type-stats")
@PreAuthorize("hasRole('ADMIN')")
public class ContentTypeController {

    private final ContentTypeRegistry registry;
    private final ContentTypeProperties properties;

    public ContentTypeController(ContentTypeRegistry registry, ContentTypeProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "total", snap.get("total"),
                "types", snap.get("types")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "total", 0L);
    }
}
