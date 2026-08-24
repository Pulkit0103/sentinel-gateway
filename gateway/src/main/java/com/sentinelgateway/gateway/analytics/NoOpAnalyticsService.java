package com.sentinelgateway.gateway.analytics;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

/**
 * No-operation implementation of {@link AnalyticsService}.
 *
 * Used when {@code sentinel.analytics.enabled=false}. All methods return empty
 * or zeroed results without touching Redis. Not annotated with {@code @Component}
 * — registered conditionally by {@link AnalyticsConfig}.
 */
public class NoOpAnalyticsService implements AnalyticsService {

    @Override
    public Mono<Void> record(AnalyticsRecord record) {
        return Mono.empty();
    }

    @Override
    public Mono<AnalyticsSummary> getSummary(LocalDate date) {
        return Mono.just(new AnalyticsSummary(
                date.toString(), 0, 0, 0, 0, 0, 0, 0, 0.0,
                List.of(), List.of()));
    }

    @Override
    public Flux<RouteStats> getRouteStats(LocalDate date) {
        return Flux.empty();
    }

    @Override
    public Flux<TenantStats> getTenantStats(LocalDate date) {
        return Flux.empty();
    }

    @Override
    public Flux<HourlyBucket> getTimeline(int hours) {
        return Flux.empty();
    }

    @Override
    public Mono<SecurityStats> getSecurityStats(LocalDate date) {
        return Mono.just(new SecurityStats(date.toString(), 0, 0, 0, 0, 0));
    }
}
