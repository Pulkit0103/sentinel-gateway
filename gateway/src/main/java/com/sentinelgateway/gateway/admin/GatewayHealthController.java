package com.sentinelgateway.gateway.admin;

import com.sentinelgateway.gateway.canary.CanaryConfig;
import com.sentinelgateway.gateway.canary.CanaryProperties;
import com.sentinelgateway.gateway.routing.RouteEntity;
import com.sentinelgateway.gateway.routing.RouteRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Admin endpoint that aggregates health and configuration of all registered routes.
 *
 * Security: protected by the existing SecurityWebFilterChain which requires ROLE_ADMIN
 * for all /admin/** paths.
 */
@RestController
@RequestMapping("/admin/health")
public class GatewayHealthController {

    private final RouteRepository routeRepository;
    private final CanaryProperties canaryProperties;

    public GatewayHealthController(RouteRepository routeRepository,
                                   CanaryProperties canaryProperties) {
        this.routeRepository = routeRepository;
        this.canaryProperties = canaryProperties;
    }

    /**
     * Returns one {@link RouteHealthReport} per registered route.
     */
    @GetMapping("/routes")
    public Flux<RouteHealthReport> getRoutes() {
        return routeRepository.findAll().map(this::toReport);
    }

    /**
     * Returns aggregated counts across all routes.
     */
    @GetMapping("/summary")
    public Mono<HealthSummary> getSummary() {
        return routeRepository.findAll()
                .map(this::toReport)
                .collectList()
                .map(this::aggregate);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private RouteHealthReport toReport(RouteEntity entity) {
        String routeId = entity.getRouteId();

        boolean canaryEnabled = canaryProperties.getRoutes().containsKey(routeId);
        int canaryWeight = 0;
        if (canaryEnabled) {
            CanaryConfig cfg = canaryProperties.getRoutes().get(routeId);
            canaryWeight = cfg != null ? cfg.getWeight() : 0;
        }

        boolean ipFilterEnabled = isNonEmpty(entity.getAllowedIps())
                || isNonEmpty(entity.getBlockedIps());

        return new RouteHealthReport(
                routeId,
                entity.getPath(),
                entity.getServiceUri(),
                entity.getMethods(),
                entity.isEnabled(),
                entity.getRateLimitPolicy(),
                entity.getCacheTtlSeconds(),
                entity.getTimeoutMs(),
                entity.getMaxBodyBytes(),
                ipFilterEnabled,
                canaryEnabled,
                canaryWeight
        );
    }

    private HealthSummary aggregate(List<RouteHealthReport> reports) {
        int total = reports.size();
        int enabled = 0;
        int withCanary = 0;
        int withCache = 0;
        int withIpFilter = 0;
        int withTimeout = 0;

        for (RouteHealthReport r : reports) {
            if (r.enabled())        enabled++;
            if (r.canaryEnabled())  withCanary++;
            if (r.cacheTtlSeconds() != null) withCache++;
            if (r.ipFilterEnabled()) withIpFilter++;
            if (r.timeoutMs() != null)       withTimeout++;
        }

        return new HealthSummary(
                total,
                enabled,
                total - enabled,
                withCanary,
                withCache,
                withIpFilter,
                withTimeout
        );
    }

    private static boolean isNonEmpty(String value) {
        return value != null && !value.isBlank();
    }
}
