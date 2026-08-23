package com.sentinelgateway.gateway.analytics;

/**
 * Immutable record representing a single request's analytics data point.
 */
public record AnalyticsRecord(
        String routeId,
        String tenantId,
        String outcome,
        int statusCode,
        String method,
        long durationMs
) {}
