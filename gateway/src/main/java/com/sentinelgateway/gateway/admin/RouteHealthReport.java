package com.sentinelgateway.gateway.admin;

/**
 * Snapshot of a single route's health and configuration for the admin dashboard.
 */
public record RouteHealthReport(
        String routeId,
        String path,
        String serviceUri,
        String methods,
        boolean enabled,
        String rateLimitPolicy,
        Integer cacheTtlSeconds,
        Long timeoutMs,
        Long maxBodyBytes,
        boolean ipFilterEnabled,
        boolean canaryEnabled,
        int canaryWeight
) {}
