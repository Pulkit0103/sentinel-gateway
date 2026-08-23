package com.sentinelgateway.gateway.analytics;

/**
 * Security-focused metrics for a given day.
 */
public record SecurityStats(
        String date,
        long blockedWaf,
        long unauthenticated,
        long unauthorized,
        long rateLimited,
        long notFound
) {}
