package com.sentinelgateway.gateway.useragent;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for request User-Agent category distribution statistics (Phase 48).
 */
@RestController
@RequestMapping("/admin/user-agent-stats")
@PreAuthorize("hasRole('ADMIN')")
public class UserAgentController {

    private final UserAgentRegistry registry;
    private final UserAgentProperties properties;

    public UserAgentController(UserAgentRegistry registry, UserAgentProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
                "enabled", properties.isEnabled(),
                "total", snap.get("total"),
                "categories", snap.get("categories")
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        registry.reset();
        return Map.of("reset", true, "total", 0L);
    }
}
