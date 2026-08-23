package com.sentinelgateway.gateway.analytics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * Admin REST API for analytics and usage reporting.
 *
 * All endpoints are under {@code /admin/analytics} and therefore require
 * {@code ROLE_ADMIN} as enforced by {@link com.sentinelgateway.gateway.security.SecurityConfig}.
 */
@RestController
@RequestMapping("/admin/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * GET /admin/analytics/summary?date=yyyy-MM-dd
     * Returns a daily traffic and outcome summary. Defaults to today.
     */
    @GetMapping("/summary")
    public Mono<AnalyticsSummary> getSummary(
            @RequestParam(required = false) String date) {
        LocalDate localDate = parseDate(date);
        return analyticsService.getSummary(localDate);
    }

    /**
     * GET /admin/analytics/routes?date=yyyy-MM-dd
     * Returns per-route request counts, highest first.
     */
    @GetMapping("/routes")
    public Flux<RouteStats> getRoutes(
            @RequestParam(required = false) String date) {
        return analyticsService.getRouteStats(parseDate(date));
    }

    /**
     * GET /admin/analytics/tenants?date=yyyy-MM-dd
     * Returns per-tenant request counts, highest first.
     */
    @GetMapping("/tenants")
    public Flux<TenantStats> getTenants(
            @RequestParam(required = false) String date) {
        return analyticsService.getTenantStats(parseDate(date));
    }

    /**
     * GET /admin/analytics/timeline?hours=N
     * Returns hourly traffic buckets for the last N hours (default 24).
     */
    @GetMapping("/timeline")
    public Flux<HourlyBucket> getTimeline(
            @RequestParam(defaultValue = "24") int hours) {
        return analyticsService.getTimeline(hours);
    }

    /**
     * GET /admin/analytics/security?date=yyyy-MM-dd
     * Returns security-focused metrics for the given date.
     */
    @GetMapping("/security")
    public Mono<SecurityStats> getSecurityStats(
            @RequestParam(required = false) String date) {
        return analyticsService.getSecurityStats(parseDate(date));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        return LocalDate.parse(date);
    }
}
