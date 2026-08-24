package com.sentinelgateway.gateway.routeblock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

/**
 * Admin endpoints for blocking and unblocking individual routes at runtime.
 *
 * All paths under /admin/** require ROLE_ADMIN (enforced by SecurityWebFilterChain).
 *
 * POST /admin/route-blocks/{routeId}        — block a route (idempotent)
 * DELETE /admin/route-blocks/{routeId}      — unblock a route (idempotent)
 * GET    /admin/route-blocks                — list currently blocked route IDs
 * DELETE /admin/route-blocks               — unblock all routes
 */
@RestController
@RequestMapping("/admin/route-blocks")
public class RouteBlockController {

    private static final Logger log = LoggerFactory.getLogger(RouteBlockController.class);

    private final RouteBlockRegistry registry;

    public RouteBlockController(RouteBlockRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public Mono<Map<String, Object>> listBlocked() {
        Set<String> blocked = registry.blockedRouteIds();
        return Mono.just(Map.of("blockedRoutes", blocked, "count", blocked.size()));
    }

    @PostMapping("/{routeId}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, Object>> blockRoute(@PathVariable String routeId) {
        log.warn("Administratively blocking route: {}", routeId);
        registry.block(routeId);
        return Mono.just(Map.of("routeId", routeId, "blocked", true));
    }

    @DeleteMapping("/{routeId}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, Object>> unblockRoute(@PathVariable String routeId) {
        log.info("Unblocking route: {}", routeId);
        registry.unblock(routeId);
        return Mono.just(Map.of("routeId", routeId, "blocked", false));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, Object>> unblockAll() {
        int count = registry.blockedRouteIds().size();
        registry.unblockAll();
        log.info("Unblocked all {} routes", count);
        return Mono.just(Map.of("unblocked", count));
    }
}
