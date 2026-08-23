package com.sentinelgateway.gateway.routing;

import java.util.List;
import java.util.Map;
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
    private final int stripPrefix;
    private final Map<String, String> addRequestHeaders;
    private final List<String> allowedIps;
    private final List<String> blockedIps;

    public RouteDefinition(
            String routeId,
            String path,
            String serviceUri,
            List<String> methods,
            boolean enabled,
            List<String> requiredScopes,
            boolean tenantRequired,
            String rateLimitPolicy) {
        this(routeId, path, serviceUri, methods, enabled, requiredScopes, tenantRequired, rateLimitPolicy, 0, Map.of());
    }

    public RouteDefinition(
            String routeId,
            String path,
            String serviceUri,
            List<String> methods,
            boolean enabled,
            List<String> requiredScopes,
            boolean tenantRequired,
            String rateLimitPolicy,
            int stripPrefix,
            Map<String, String> addRequestHeaders) {
        this(routeId, path, serviceUri, methods, enabled, requiredScopes, tenantRequired, rateLimitPolicy,
                stripPrefix, addRequestHeaders, List.of(), List.of());
    }

    public RouteDefinition(
            String routeId,
            String path,
            String serviceUri,
            List<String> methods,
            boolean enabled,
            List<String> requiredScopes,
            boolean tenantRequired,
            String rateLimitPolicy,
            int stripPrefix,
            Map<String, String> addRequestHeaders,
            List<String> allowedIps,
            List<String> blockedIps) {

        this.routeId = Objects.requireNonNull(routeId, "routeId must not be null");
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.serviceUri = Objects.requireNonNull(serviceUri, "serviceUri must not be null");
        this.methods = methods != null ? List.copyOf(methods) : List.of();
        this.enabled = enabled;
        this.requiredScopes = requiredScopes != null ? List.copyOf(requiredScopes) : List.of();
        this.tenantRequired = tenantRequired;
        this.rateLimitPolicy = rateLimitPolicy != null ? rateLimitPolicy : "DEFAULT";
        this.stripPrefix = stripPrefix;
        this.addRequestHeaders = addRequestHeaders != null ? Map.copyOf(addRequestHeaders) : Map.of();
        this.allowedIps = allowedIps != null ? List.copyOf(allowedIps) : List.of();
        this.blockedIps = blockedIps != null ? List.copyOf(blockedIps) : List.of();
    }

    public String routeId()         { return routeId; }
    public String path()            { return path; }
    public String serviceUri()      { return serviceUri; }
    public List<String> methods()   { return methods; }
    public boolean enabled()        { return enabled; }
    public List<String> requiredScopes() { return requiredScopes; }
    public boolean tenantRequired() { return tenantRequired; }
    public String rateLimitPolicy() { return rateLimitPolicy; }
    public int stripPrefix()        { return stripPrefix; }
    public Map<String, String> addRequestHeaders() { return addRequestHeaders; }
    public List<String> allowedIps() { return allowedIps; }
    public List<String> blockedIps() { return blockedIps; }

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
