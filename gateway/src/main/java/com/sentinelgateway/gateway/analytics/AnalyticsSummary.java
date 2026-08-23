package com.sentinelgateway.gateway.analytics;

import java.util.List;

/**
 * Daily analytics summary returned by the admin API.
 */
public record AnalyticsSummary(
        String date,
        long totalRequests,
        long allowedRequests,
        long blockedWaf,
        long unauthenticated,
        long unauthorized,
        long rateLimited,
        long serviceErrors,
        double errorRate,
        List<RouteStats> topRoutes,
        List<TenantStats> topTenants
) {}
