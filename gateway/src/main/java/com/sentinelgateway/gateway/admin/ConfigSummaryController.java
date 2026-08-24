package com.sentinelgateway.gateway.admin;

import com.sentinelgateway.gateway.ipaccess.IpAccessProperties;
import com.sentinelgateway.gateway.maintenance.MaintenanceProperties;
import com.sentinelgateway.gateway.metrics.RequestMetricsRegistry;
import com.sentinelgateway.gateway.sanitizer.RequestSanitizerProperties;
import com.sentinelgateway.gateway.security.ClaimAuthorizationProperties;
import com.sentinelgateway.gateway.security.JwtAudienceProperties;
import com.sentinelgateway.gateway.security.SecurityHeadersProperties;
import com.sentinelgateway.gateway.session.ActiveSessionRegistry;
import com.sentinelgateway.gateway.sla.SlaProperties;
import com.sentinelgateway.gateway.slowrequest.SlowRequestProperties;
import com.sentinelgateway.gateway.uptime.UptimeRegistry;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin endpoint providing a single-page summary of all feature toggles and key metrics (Phase 34).
 *
 * GET /admin/config — returns a snapshot of enabled/disabled status for every feature,
 * plus current gateway stats (uptime, request count, active sessions).
 *
 * Requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin/config")
public class ConfigSummaryController {

    private final MaintenanceProperties maintenanceProperties;
    private final IpAccessProperties ipAccessProperties;
    private final SecurityHeadersProperties securityHeadersProperties;
    private final RequestSanitizerProperties sanitizerProperties;
    private final SlowRequestProperties slowRequestProperties;
    private final SlaProperties slaProperties;
    private final ClaimAuthorizationProperties claimAuthorizationProperties;
    private final JwtAudienceProperties jwtAudienceProperties;
    private final UptimeRegistry uptimeRegistry;
    private final ActiveSessionRegistry sessionRegistry;
    private final RequestMetricsRegistry metricsRegistry;

    public ConfigSummaryController(
            MaintenanceProperties maintenanceProperties,
            IpAccessProperties ipAccessProperties,
            SecurityHeadersProperties securityHeadersProperties,
            RequestSanitizerProperties sanitizerProperties,
            SlowRequestProperties slowRequestProperties,
            SlaProperties slaProperties,
            ClaimAuthorizationProperties claimAuthorizationProperties,
            JwtAudienceProperties jwtAudienceProperties,
            UptimeRegistry uptimeRegistry,
            ActiveSessionRegistry sessionRegistry,
            RequestMetricsRegistry metricsRegistry) {
        this.maintenanceProperties = maintenanceProperties;
        this.ipAccessProperties = ipAccessProperties;
        this.securityHeadersProperties = securityHeadersProperties;
        this.sanitizerProperties = sanitizerProperties;
        this.slowRequestProperties = slowRequestProperties;
        this.slaProperties = slaProperties;
        this.claimAuthorizationProperties = claimAuthorizationProperties;
        this.jwtAudienceProperties = jwtAudienceProperties;
        this.uptimeRegistry = uptimeRegistry;
        this.sessionRegistry = sessionRegistry;
        this.metricsRegistry = metricsRegistry;
    }

    @GetMapping
    public Mono<Map<String, Object>> getSummary() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("maintenance", maintenanceProperties.isEnabled());
        features.put("ipAccess", ipAccessProperties.isEnabled());
        features.put("securityHeaders", securityHeadersProperties.isEnabled());
        features.put("requestSanitizer", sanitizerProperties.isEnabled());
        features.put("slowRequestDetection", slowRequestProperties.isEnabled());
        features.put("slaTracking", slaProperties.isEnabled());
        features.put("claimAuthorization", claimAuthorizationProperties.isEnabled());
        features.put("jwtAudienceValidation", jwtAudienceProperties.isAudienceValidationEnabled());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("uptimeSeconds", uptimeRegistry.uptimeSeconds());
        stats.put("requestCount", uptimeRegistry.getRequestCount());
        stats.put("totalTrackedSessions", sessionRegistry.totalTracked());
        stats.put("inflightRequests", metricsRegistry.getInflightRequests());
        stats.put("totalCompletedRequests", metricsRegistry.getTotalCompleted());

        return Mono.just(Map.of(
                "startTime", uptimeRegistry.getStartTime().toString(),
                "features", features,
                "stats", stats
        ));
    }
}
