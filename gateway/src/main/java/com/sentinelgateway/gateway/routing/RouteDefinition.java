package com.sentinelgateway.gateway.routing;

import java.util.List;
import java.util.Objects;

/**
 * Immutable domain model for a single gateway route.
 *
 * Routes are loaded from configuration at startup. Later phases will add
 * persistence (PostgreSQL) and dynamic reload, but the domain model stays
 * the same — the only change will be the source of the data.
 *
 * Fields intentionally parallel the Phase 2 spec fields:
 *   routeId, path, service, methods, enabled, requiredScopes,
 *   tenantRequired, rateLimitPolicy
 */
public final class RouteDefinition {

    private final String routeId;
    private final String path;
    private final String serviceUri;
    private final List<String> methods;
    private final boolean enabled;
    private final List<String> requiredScopes;
    private final boolean tenantRequired;
    private final String rateLimitPolicy;

    public RouteDefinition(
            String routeId,
            String path,
            String serviceUri,
            List<String> methods,
            boolean enabled,
            List<String> requiredScopes,
            boolean tenantRequired,
            String rateLimitPolicy) {

        this.routeId = Objects.requireNonNull(routeId, "routeId must not be null");
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.serviceUri = Objects.requireNonNull(serviceUri, "serviceUri must not be null");
        this.methods = methods != null ? List.copyOf(methods) : List.of();
        this.enabled = enabled;
        this.requiredScopes = requiredScopes != null ? List.copyOf(requiredScopes) : List.of();
        this.tenantRequired = tenantRequired;
        this.rateLimitPolicy = rateLimitPolicy != null ? rateLimitPolicy : "DEFAULT";
    }

    public String routeId()         { return routeId; }
    public String path()            { return path; }
    public String serviceUri()      { return serviceUri; }
    public List<String> methods()   { return methods; }
    public boolean enabled()        { return enabled; }
    public List<String> requiredScopes() { return requiredScopes; }
    public boolean tenantRequired() { return tenantRequired; }
    public String rateLimitPolicy() { return rateLimitPolicy; }

    @Override
    public String toString() {
        return "RouteDefinition{routeId='" + routeId + "', path='" + path +
                "', serviceUri='" + serviceUri + "', enabled=" + enabled + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RouteDefinition that)) return false;
        return Objects.equals(routeId, that.routeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(routeId);
    }
}
