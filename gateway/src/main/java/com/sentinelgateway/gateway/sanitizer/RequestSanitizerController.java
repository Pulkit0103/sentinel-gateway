package com.sentinelgateway.gateway.sanitizer;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin endpoint for inspecting the request sanitizer configuration (Phase 32).
 *
 * GET /admin/request-sanitizer — returns enabled, maxHeaderValueLength, blockNullBytes.
 *
 * Requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin/request-sanitizer")
public class RequestSanitizerController {

    private final RequestSanitizerProperties properties;

    public RequestSanitizerController(RequestSanitizerProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public Mono<Map<String, Object>> getConfig() {
        return Mono.just(Map.of(
                "enabled", properties.isEnabled(),
                "maxHeaderValueLength", properties.getMaxHeaderValueLength(),
                "blockNullBytes", properties.isBlockNullBytes()
        ));
    }
}
