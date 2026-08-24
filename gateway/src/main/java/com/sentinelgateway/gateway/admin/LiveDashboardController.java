package com.sentinelgateway.gateway.admin;

import com.sentinelgateway.gateway.concurrent.ConcurrentRequestRegistry;
import com.sentinelgateway.gateway.errorrate.ErrorRateRegistry;
import com.sentinelgateway.gateway.health.HealthScoreService;
import com.sentinelgateway.gateway.hopcount.HopCountRegistry;
import com.sentinelgateway.gateway.latency.LatencyRegistry;
import com.sentinelgateway.gateway.requestsize.OversizedRequestRegistry;
import com.sentinelgateway.gateway.responsesize.ResponseSizeRegistry;
import com.sentinelgateway.gateway.session.ActiveSessionRegistry;
import com.sentinelgateway.gateway.slowrequest.SlowRequestRegistry;
import com.sentinelgateway.gateway.statuscode.StatusCodeRegistry;
import com.sentinelgateway.gateway.uptime.UptimeRegistry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregated live operations dashboard (Phase 45).
 *
 * Single endpoint combining health score, uptime, concurrency, error rates,
 * latency, slow requests, status codes, session count, and size metrics.
 * Useful for operations dashboards and incident triage.
 */
@RestController
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class LiveDashboardController {

    private final HealthScoreService healthScoreService;
    private final UptimeRegistry uptimeRegistry;
    private final ConcurrentRequestRegistry concurrentRegistry;
    private final ErrorRateRegistry errorRateRegistry;
    private final SlowRequestRegistry slowRequestRegistry;
    private final StatusCodeRegistry statusCodeRegistry;
    private final LatencyRegistry latencyRegistry;
    private final ActiveSessionRegistry sessionRegistry;
    private final OversizedRequestRegistry oversizedRegistry;
    private final ResponseSizeRegistry responseSizeRegistry;
    private final HopCountRegistry hopCountRegistry;

    public LiveDashboardController(
            HealthScoreService healthScoreService,
            UptimeRegistry uptimeRegistry,
            ConcurrentRequestRegistry concurrentRegistry,
            ErrorRateRegistry errorRateRegistry,
            SlowRequestRegistry slowRequestRegistry,
            StatusCodeRegistry statusCodeRegistry,
            LatencyRegistry latencyRegistry,
            ActiveSessionRegistry sessionRegistry,
            OversizedRequestRegistry oversizedRegistry,
            ResponseSizeRegistry responseSizeRegistry,
            HopCountRegistry hopCountRegistry) {
        this.healthScoreService = healthScoreService;
        this.uptimeRegistry = uptimeRegistry;
        this.concurrentRegistry = concurrentRegistry;
        this.errorRateRegistry = errorRateRegistry;
        this.slowRequestRegistry = slowRequestRegistry;
        this.statusCodeRegistry = statusCodeRegistry;
        this.latencyRegistry = latencyRegistry;
        this.sessionRegistry = sessionRegistry;
        this.oversizedRegistry = oversizedRegistry;
        this.responseSizeRegistry = responseSizeRegistry;
        this.hopCountRegistry = hopCountRegistry;
    }

    @GetMapping
    public Map<String, Object> getDashboard() {
        Map<String, Object> dashboard = new LinkedHashMap<>();

        dashboard.put("timestamp", Instant.now().toString());
        dashboard.put("health", healthScoreService.compute());

        Map<String, Object> uptime = new LinkedHashMap<>();
        uptime.put("startTime", uptimeRegistry.getStartTime().toString());
        uptime.put("uptimeSeconds", uptimeRegistry.uptimeSeconds());
        uptime.put("totalRequests", uptimeRegistry.getRequestCount());
        dashboard.put("uptime", uptime);

        Map<String, Object> concurrency = new LinkedHashMap<>();
        concurrency.put("current", concurrentRegistry.getCurrent());
        concurrency.put("peak", concurrentRegistry.getPeak());
        concurrency.put("totalCompleted", concurrentRegistry.getTotalCompleted());
        dashboard.put("concurrency", concurrency);

        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("routeCount", errorRateRegistry.routeCount());
        long totalRouteRequests = errorRateRegistry.snapshots().values().stream()
                .mapToLong(s -> ((Number) s.get("total")).longValue()).sum();
        long totalRouteErrors = errorRateRegistry.snapshots().values().stream()
                .mapToLong(s -> ((Number) s.get("errors")).longValue()).sum();
        errors.put("totalRequests", totalRouteRequests);
        errors.put("totalErrors", totalRouteErrors);
        dashboard.put("errors", errors);

        Map<String, Object> statusCodes = statusCodeRegistry.snapshot();
        dashboard.put("statusCodes", Map.of(
                "total", statusCodes.get("total"),
                "buckets", statusCodes.get("buckets")
        ));

        Map<String, Object> latency = new LinkedHashMap<>();
        latency.put("routeCount", latencyRegistry.routeCount());
        dashboard.put("latency", latency);

        Map<String, Object> slowReqs = new LinkedHashMap<>();
        slowReqs.put("recordedCount", slowRequestRegistry.size());
        dashboard.put("slowRequests", slowReqs);

        Map<String, Object> sessions = new LinkedHashMap<>();
        sessions.put("activeCount", sessionRegistry.totalTracked());
        dashboard.put("sessions", sessions);

        Map<String, Object> sizes = new LinkedHashMap<>();
        sizes.put("oversizedRequestCount", oversizedRegistry.count());
        sizes.put("responseSizeSamples", responseSizeRegistry.getSampleCount());
        sizes.put("responseSizeAvgBytes", responseSizeRegistry.getAverageBytes());
        dashboard.put("sizes", sizes);

        Map<String, Object> hops = hopCountRegistry.snapshot();
        dashboard.put("hops", Map.of(
                "totalProcessed", hops.get("totalProcessed"),
                "totalRejected", hops.get("totalRejected")
        ));

        return dashboard;
    }
}
