package com.sentinelgateway.gateway.admin;

/**
 * Aggregated health summary across all registered routes.
 */
public record HealthSummary(
        int totalRoutes,
        int enabledRoutes,
        int disabledRoutes,
        int routesWithCanary,
        int routesWithCache,
        int routesWithIpFilter,
        int routesWithTimeout
) {}
