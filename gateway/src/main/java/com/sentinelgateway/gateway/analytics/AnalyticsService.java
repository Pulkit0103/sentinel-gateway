package com.sentinelgateway.gateway.analytics;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * Contract for recording and querying gateway analytics.
 *
 * Implementations must be non-blocking — all methods return reactive types.
 * Failures inside implementations must never propagate to the request path.
 */
public interface AnalyticsService {

    /**
     * Record a single request's analytics data. Fire-and-forget; must not fail the caller.
     */
    Mono<Void> record(AnalyticsRecord record);

    /**
     * Return a daily summary of traffic and outcome counts.
     */
    Mono<AnalyticsSummary> getSummary(LocalDate date);

    /**
     * Return per-route request counts for the given date, highest first.
     */
    Flux<RouteStats> getRouteStats(LocalDate date);

    /**
     * Return per-tenant request counts for the given date, highest first.
     */
    Flux<TenantStats> getTenantStats(LocalDate date);

    /**
     * Return hourly traffic buckets for the last {@code hours} hours.
     */
    Flux<HourlyBucket> getTimeline(int hours);

    /**
     * Return security-focused metrics for the given date.
     */
    Mono<SecurityStats> getSecurityStats(LocalDate date);
}
