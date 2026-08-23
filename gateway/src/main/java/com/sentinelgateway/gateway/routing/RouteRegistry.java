package com.sentinelgateway.gateway.routing;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-memory registry of active route definitions.
 *
 * Phase 2: routes are loaded once at startup from configuration.
 * Future phases will add:
 *  - Admin API to add/update/disable routes at runtime (Phase 17)
 *  - PostgreSQL persistence for durable route storage (Phase 6+)
 *  - Kafka event publishing on route changes (Phase 13+)
 *
 * The registry is the single source of truth for route decisions within
 * the gateway process. All filters that need to know about a route should
 * consult this registry rather than re-reading configuration.
 */
@Component
public class RouteRegistry {

    private final Map<String, RouteDefinition> routesById;

    public RouteRegistry(RouteDefinitionProperties properties) {
        this.routesById = properties.toRouteDefinitions().stream()
                .collect(Collectors.toUnmodifiableMap(
                        RouteDefinition::routeId,
                        Function.identity()
                ));
    }

    /** All registered routes (enabled and disabled). */
    public List<RouteDefinition> allRoutes() {
        return List.copyOf(routesById.values());
    }

    /** Only enabled routes. */
    public List<RouteDefinition> enabledRoutes() {
        return routesById.values().stream()
                .filter(RouteDefinition::enabled)
                .toList();
    }

    /** Look up a route by its routeId. */
    public Optional<RouteDefinition> findById(String routeId) {
        return Optional.ofNullable(routesById.get(routeId));
    }

    public int size() {
        return routesById.size();
    }
}
