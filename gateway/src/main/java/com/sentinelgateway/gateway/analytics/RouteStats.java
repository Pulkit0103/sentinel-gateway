package com.sentinelgateway.gateway.analytics;

/**
 * Aggregated request count for a single route.
 */
public record RouteStats(String routeId, long requests) {}
