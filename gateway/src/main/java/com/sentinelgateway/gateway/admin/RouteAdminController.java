package com.sentinelgateway.gateway.admin;

import com.sentinelgateway.gateway.routing.RouteDefinition;
import com.sentinelgateway.gateway.routing.RouteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin REST API for route lifecycle management.
 *
 * All mutating operations (POST/PUT/DELETE/enable/disable) immediately
 * update the in-memory RouteRegistry and publish a RefreshRoutesEvent so
 * Spring Cloud Gateway picks up the change without a restart.
 *
 * Secured by ROLE_ADMIN (configured in SecurityConfig).
 */
@RestController
@RequestMapping("/admin/routes")
public class RouteAdminController {

    private final RouteService routeService;

    public RouteAdminController(RouteService routeService) {
        this.routeService = routeService;
    }

    /** List all routes (enabled and disabled). */
    @GetMapping
    public Flux<RouteResponse> listRoutes() {
        return routeService.findAll().map(RouteResponse::from);
    }

    /** Get a single route by ID. */
    @GetMapping("/{routeId}")
    public Mono<RouteResponse> getRoute(@PathVariable String routeId) {
        return routeService.findById(routeId).map(RouteResponse::from);
    }

    /** Create a new route. Takes effect immediately. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RouteResponse> createRoute(@Valid @RequestBody CreateRouteRequest request) {
        RouteDefinition definition = new RouteDefinition(
                request.routeId(), request.path(), request.serviceUri(),
                request.methods() != null ? request.methods() : List.of(),
                request.enabled() != null ? request.enabled() : true,
                request.requiredScopes() != null ? request.requiredScopes() : List.of(),
                request.tenantRequired() != null ? request.tenantRequired() : false,
                request.rateLimitPolicy() != null ? request.rateLimitPolicy() : "DEFAULT"
        );
        return routeService.create(definition).map(RouteResponse::from);
    }

    /** Update an existing route. Takes effect immediately. */
    @PutMapping("/{routeId}")
    public Mono<RouteResponse> updateRoute(@PathVariable String routeId,
                                           @Valid @RequestBody UpdateRouteRequest request) {
        RouteDefinition updated = new RouteDefinition(
                routeId,
                request.path(), request.serviceUri(),
                request.methods() != null ? request.methods() : List.of(),
                request.enabled() != null ? request.enabled() : true,
                request.requiredScopes() != null ? request.requiredScopes() : List.of(),
                request.tenantRequired() != null ? request.tenantRequired() : false,
                request.rateLimitPolicy() != null ? request.rateLimitPolicy() : "DEFAULT"
        );
        return routeService.update(routeId, updated).map(RouteResponse::from);
    }

    /** Delete a route permanently. Takes effect immediately. */
    @DeleteMapping("/{routeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteRoute(@PathVariable String routeId) {
        return routeService.delete(routeId);
    }

    /** Enable a route without changing other attributes. */
    @PostMapping("/{routeId}/enable")
    public Mono<RouteResponse> enableRoute(@PathVariable String routeId) {
        return routeService.setEnabled(routeId, true).map(RouteResponse::from);
    }

    /** Disable a route without deleting it. Requests will receive 404. */
    @PostMapping("/{routeId}/disable")
    public Mono<RouteResponse> disableRoute(@PathVariable String routeId) {
        return routeService.setEnabled(routeId, false).map(RouteResponse::from);
    }

    // ── Request / Response Records ────────────────────────────────────────────

    record CreateRouteRequest(
            @NotBlank String routeId,
            @NotBlank String path,
            @NotBlank String serviceUri,
            List<String> methods,
            Boolean enabled,
            List<String> requiredScopes,
            Boolean tenantRequired,
            @Pattern(regexp = "ANONYMOUS|USER|PREMIUM|DEFAULT") String rateLimitPolicy
    ) {}

    record UpdateRouteRequest(
            @NotBlank String path,
            @NotBlank String serviceUri,
            List<String> methods,
            Boolean enabled,
            List<String> requiredScopes,
            Boolean tenantRequired,
            @Pattern(regexp = "ANONYMOUS|USER|PREMIUM|DEFAULT") String rateLimitPolicy
    ) {}

    record RouteResponse(
            String routeId, String path, String serviceUri,
            List<String> methods, boolean enabled,
            List<String> requiredScopes, boolean tenantRequired,
            String rateLimitPolicy, LocalDateTime updatedAt
    ) {
        static RouteResponse from(RouteDefinition d) {
            return new RouteResponse(
                    d.routeId(), d.path(), d.serviceUri(),
                    d.methods(), d.enabled(), d.requiredScopes(),
                    d.tenantRequired(), d.rateLimitPolicy(), null
            );
        }
    }
}
