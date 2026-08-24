package com.sentinelgateway.gateway.admin;

import com.sentinelgateway.gateway.clockskew.ClockSkewRegistry;
import com.sentinelgateway.gateway.concurrent.ConcurrentRequestRegistry;
import com.sentinelgateway.gateway.contenttype.ContentTypeRegistry;
import com.sentinelgateway.gateway.encodingstats.EncodingStatsRegistry;
import com.sentinelgateway.gateway.errorrate.ErrorRateRegistry;
import com.sentinelgateway.gateway.headeraudit.HeaderAuditRegistry;
import com.sentinelgateway.gateway.health.HealthScoreService;
import com.sentinelgateway.gateway.hopcount.HopCountRegistry;
import com.sentinelgateway.gateway.ipcounter.IpCounterRegistry;
import com.sentinelgateway.gateway.latency.LatencyRegistry;
import com.sentinelgateway.gateway.methodstats.MethodRegistry;
import com.sentinelgateway.gateway.pathdepth.PathDepthRegistry;
import com.sentinelgateway.gateway.protocolstats.ProtocolStatsRegistry;
import com.sentinelgateway.gateway.queryparam.QueryParamRegistry;
import com.sentinelgateway.gateway.refererstat.RefererStatRegistry;
import com.sentinelgateway.gateway.requestsize.OversizedRequestRegistry;
import com.sentinelgateway.gateway.responsesize.ResponseSizeRegistry;
import com.sentinelgateway.gateway.routetraffic.RouteTrafficRegistry;
import com.sentinelgateway.gateway.schemestat.SchemeStatRegistry;
import com.sentinelgateway.gateway.session.ActiveSessionRegistry;
import com.sentinelgateway.gateway.slowrequest.SlowRequestRegistry;
import com.sentinelgateway.gateway.statuscode.StatusCodeRegistry;
import com.sentinelgateway.gateway.uptime.UptimeRegistry;
import com.sentinelgateway.gateway.useragent.UserAgentRegistry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregated live operations dashboard (Phase 45, extended in Phase 55 and 64).
 *
 * Single endpoint combining all monitoring data from Phases 30-63.
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
    private final MethodRegistry methodRegistry;
    private final ContentTypeRegistry contentTypeRegistry;
    private final UserAgentRegistry userAgentRegistry;
    private final IpCounterRegistry ipCounterRegistry;
    private final ClockSkewRegistry clockSkewRegistry;
    private final HeaderAuditRegistry headerAuditRegistry;
    private final ProtocolStatsRegistry acceptStatsRegistry;
    private final RouteTrafficRegistry routeTrafficRegistry;
    private final EncodingStatsRegistry encodingStatsRegistry;
    private final QueryParamRegistry queryParamRegistry;
    private final SchemeStatRegistry schemeStatRegistry;
    private final PathDepthRegistry pathDepthRegistry;
    private final RefererStatRegistry refererStatRegistry;

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
            HopCountRegistry hopCountRegistry,
            MethodRegistry methodRegistry,
            ContentTypeRegistry contentTypeRegistry,
            UserAgentRegistry userAgentRegistry,
            IpCounterRegistry ipCounterRegistry,
            ClockSkewRegistry clockSkewRegistry,
            HeaderAuditRegistry headerAuditRegistry,
            ProtocolStatsRegistry acceptStatsRegistry,
            RouteTrafficRegistry routeTrafficRegistry,
            EncodingStatsRegistry encodingStatsRegistry,
            QueryParamRegistry queryParamRegistry,
            SchemeStatRegistry schemeStatRegistry,
            PathDepthRegistry pathDepthRegistry,
            RefererStatRegistry refererStatRegistry) {
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
        this.methodRegistry = methodRegistry;
        this.contentTypeRegistry = contentTypeRegistry;
        this.userAgentRegistry = userAgentRegistry;
        this.ipCounterRegistry = ipCounterRegistry;
        this.clockSkewRegistry = clockSkewRegistry;
        this.headerAuditRegistry = headerAuditRegistry;
        this.acceptStatsRegistry = acceptStatsRegistry;
        this.routeTrafficRegistry = routeTrafficRegistry;
        this.encodingStatsRegistry = encodingStatsRegistry;
        this.queryParamRegistry = queryParamRegistry;
        this.schemeStatRegistry = schemeStatRegistry;
        this.pathDepthRegistry = pathDepthRegistry;
        this.refererStatRegistry = refererStatRegistry;
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

        // Phase 46: method distribution
        Map<String, Object> methodSnap = methodRegistry.snapshot();
        dashboard.put("methods", Map.of("total", methodSnap.get("total"), "distribution", methodSnap.get("methods")));

        // Phase 47: response content-type distribution
        Map<String, Object> ctSnap = contentTypeRegistry.snapshot();
        dashboard.put("responseContentTypes", Map.of("total", ctSnap.get("total"), "types", ctSnap.get("types")));

        // Phase 48: user-agent categories
        Map<String, Object> uaSnap = userAgentRegistry.snapshot();
        dashboard.put("userAgents", Map.of("total", uaSnap.get("total"), "categories", uaSnap.get("categories")));

        // Phase 49: top-5 IPs
        Map<String, Object> ipSnap = ipCounterRegistry.snapshot(5);
        dashboard.put("topIps", Map.of("total", ipSnap.get("total"), "uniqueIps", ipSnap.get("uniqueIps"), "top5", ipSnap.get("topIps")));

        // Phase 50: clock skew
        Map<String, Object> skewSnap = clockSkewRegistry.snapshot();
        dashboard.put("clockSkew", Map.of("totalChecked", skewSnap.get("totalChecked"), "totalSkewed", skewSnap.get("totalSkewed")));

        // Phase 51: header audit
        Map<String, Object> auditSnap = headerAuditRegistry.snapshot();
        dashboard.put("headerAudit", Map.of("totalResponses", auditSnap.get("totalResponses"), "coveragePercent", auditSnap.get("coveragePercent")));

        // Phase 52: accept header distribution
        Map<String, Object> acceptSnap = acceptStatsRegistry.snapshot();
        dashboard.put("acceptHeaders", Map.of("total", acceptSnap.get("total"), "types", acceptSnap.get("versions")));

        // Phase 53: route windowed traffic
        dashboard.put("routeTraffic", routeTrafficRegistry.snapshot());

        // Phase 54: encoding distribution
        Map<String, Object> encSnap = encodingStatsRegistry.snapshot();
        dashboard.put("encodings", Map.of("total", encSnap.get("total"), "distribution", encSnap.get("encodings")));

        // Phase 60: query parameter count distribution
        Map<String, Object> qpSnap = queryParamRegistry.snapshot();
        dashboard.put("queryParams", Map.of("total", qpSnap.get("total"), "maxSeen", qpSnap.get("maxSeen"), "buckets", qpSnap.get("buckets")));

        // Phase 61: request scheme distribution
        Map<String, Object> schemeSnap = schemeStatRegistry.snapshot();
        dashboard.put("schemes", Map.of("total", schemeSnap.get("total"), "distribution", schemeSnap.get("schemes")));

        // Phase 62: path depth distribution
        Map<String, Object> depthSnap = pathDepthRegistry.snapshot();
        dashboard.put("pathDepth", Map.of("total", depthSnap.get("total"), "maxSeen", depthSnap.get("maxSeen"), "buckets", depthSnap.get("buckets")));

        // Phase 63: referer domain top-5
        Map<String, Object> refSnap = refererStatRegistry.snapshot(5);
        dashboard.put("referers", Map.of("total", refSnap.get("total"), "top5", refSnap.get("topDomains")));

        return dashboard;
    }
}
