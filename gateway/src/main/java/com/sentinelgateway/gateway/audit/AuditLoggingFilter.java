package com.sentinelgateway.gateway.audit;

import com.sentinelgateway.gateway.analytics.AnalyticsRecord;
import com.sentinelgateway.gateway.analytics.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Instant;

/**
 * Emits a structured {@link AuditEvent} for every request processed by the gateway,
 * and records an {@link AnalyticsRecord} in parallel.
 *
 * Runs as the outermost WebFilter (order {@code HIGHEST_PRECEDENCE + 1}) so it
 * wraps threat detection, authentication, authorization, and routing.  The audit
 * event is published AFTER the response is determined — using {@code .then()} on
 * the filter chain — ensuring the response status is captured correctly.
 *
 * The path is logged but not the query string (query strings may contain sensitive
 * user input; audit consumers should enrich from their own data stores if needed).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuditLoggingFilter implements WebFilter {

    private static final String START_TIME_ATTR = "_sg_start_ms";

    private static final Logger log = LoggerFactory.getLogger(AuditLoggingFilter.class);

    private final AuditEventPublisher publisher;
    private final AnalyticsService analytics;

    public AuditLoggingFilter(AuditEventPublisher publisher, AnalyticsService analytics) {
        this.publisher = publisher;
        this.analytics = analytics;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());
        return chain.filter(exchange)
                .then(Mono.defer(() -> publishAuditEvent(exchange)))
                .onErrorResume(ex -> Mono.defer(() -> publishAuditEvent(exchange))
                        .then(Mono.error(ex)));
    }

    private Mono<Void> publishAuditEvent(ServerWebExchange exchange) {
        ServerHttpRequest req = exchange.getRequest();

        String requestId = req.getHeaders().getFirst("X-Request-ID");
        String clientIp  = extractClientIp(req);
        String method    = req.getMethod().name();
        String path      = req.getPath().value();

        HttpStatusCode status = exchange.getResponse().getStatusCode();
        int statusCode = status != null ? status.value() : 0;

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : null;

        String outcome = AuditEvent.outcomeFor(statusCode);

        AuditEvent event = new AuditEvent(
                requestId,
                Instant.now().toString(),
                clientIp,
                method,
                path,
                routeId,
                statusCode,
                outcome
        );

        // Compute request duration
        Long startMs = exchange.getAttribute(START_TIME_ATTR);
        long durationMs = startMs != null ? System.currentTimeMillis() - startMs : 0L;

        // Tenant from request header
        String tenantId = req.getHeaders().getFirst("X-Tenant-Id");

        AnalyticsRecord analyticsRecord = new AnalyticsRecord(
                routeId, tenantId, outcome, statusCode, method, durationMs);

        Mono<Void> auditOp = publisher.publish(event)
                .onErrorResume(e -> {
                    log.error("Failed to publish audit event for {} {}: {}", method, path, e.getMessage());
                    return Mono.empty();
                });

        Mono<Void> analyticsOp = analytics.record(analyticsRecord)
                .onErrorResume(e -> {
                    log.warn("Failed to record analytics for {} {}: {}", method, path, e.getMessage());
                    return Mono.empty();
                });

        return Mono.when(auditOp, analyticsOp);
    }

    private String extractClientIp(ServerHttpRequest req) {
        String forwarded = req.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress addr = req.getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }
}
