package com.sentinelgateway.gateway.ipaccess;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin endpoint for inspecting IP access control configuration.
 *
 * GET /admin/ip-access — returns current mode, global list, and per-route overrides.
 *
 * Requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin/ip-access")
public class IpAccessController {

    private final IpAccessProperties properties;

    public IpAccessController(IpAccessProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public Mono<Map<String, Object>> getConfig() {
        return Mono.just(Map.of(
                "enabled", properties.isEnabled(),
                "mode", properties.getMode().name(),
                "globalList", properties.getGlobalList(),
                "routes", properties.getRoutes()
        ));
    }
}
