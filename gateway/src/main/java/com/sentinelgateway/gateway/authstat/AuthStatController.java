package com.sentinelgateway.gateway.authstat;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/auth-stats")
public class AuthStatController {

    private final AuthStatRegistry registry;
    private final AuthStatProperties properties;

    public AuthStatController(AuthStatRegistry registry, AuthStatProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getStats() {
        Map<String, Object> snap = registry.snapshot();
        return Map.of(
            "enabled", properties.isEnabled(),
            "total", snap.get("total"),
            "types", snap.get("types")
        );
    }

    @PostMapping("/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> reset() {
        registry.reset();
        return Map.of("status", "reset");
    }
}
