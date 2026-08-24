package com.sentinelgateway.gateway.security;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin endpoint for inspecting the active security headers configuration (Phase 29).
 *
 * GET /admin/security-headers — returns enabled flag and effective header map.
 *
 * Requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin/security-headers")
public class SecurityHeadersController {

    private final SecurityHeadersProperties properties;

    public SecurityHeadersController(SecurityHeadersProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public Mono<Map<String, Object>> getConfig() {
        return Mono.just(Map.of(
                "enabled", properties.isEnabled(),
                "headers", properties.effectiveHeaders()
        ));
    }
}
