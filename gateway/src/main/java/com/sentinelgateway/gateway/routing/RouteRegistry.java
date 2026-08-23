package com.sentinelgateway.gateway.routing;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of active route definitions — the single source of truth
 * for routing decisions inside the gateway process.
 *
 * Phase 1: populated at startup from YAML (RouteDefinitionProperties).
 * Phase 2: populated from the database by RouteService, mutated at runtime
 *          via Admin API CRUD operations.
 *
 * All mutating methods are thread-safe via ConcurrentHashMap.
 */
@Component
public class RouteRegistry {

    private final ConcurrentHashMap<String, RouteDefinition> routesById = new ConcurrentHashMap<>();

    public RouteRegistry() {}

    /** Bulk-load routes (replaces entire registry). Called by RouteService at startup. */
    public void loadAll(Collection<RouteDefinition> routes) {
        routesById.clear();
        routes.forEach(r -> routesById.put(r.routeId(), r));
    }

    /** Add or replace a single route definition. */
    public void register(RouteDefinition route) {
        routesById.put(route.routeId(), route);
    }

    /** Remove a route by ID. No-op if not present. */
    public void deregister(String routeId) {
        routesById.remove(routeId);
    }

    /** All registered routes (enabled and disabled). */
    public List<RouteDefinition> allRoutes() {
        return List.copyOf(routesById.values());
    }

    /** Only enabled routes — used by routing filters. */
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
