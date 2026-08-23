package com.sentinelgateway.gateway.analytics;

/**
 * Hourly traffic bucket for timeline visualisation.
 */
public record HourlyBucket(String hour, long total, long errors, long blocked, long rateLimited) {}
