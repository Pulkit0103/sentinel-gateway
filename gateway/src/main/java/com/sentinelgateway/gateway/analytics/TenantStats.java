package com.sentinelgateway.gateway.analytics;

/**
 * Aggregated request count for a single tenant.
 */
public record TenantStats(String tenantId, long requests) {}
