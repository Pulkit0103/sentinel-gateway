package com.sentinelgateway.gateway.metrics;

import com.sentinelgateway.gateway.audit.AuditEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * Records per-request metrics to Micrometer.
 *
 * Runs as the outermost WebFilter (HIGHEST_PRECEDENCE) so it measures
 * total request time including security checks, routing, and upstream I/O.
 *
 * Metrics emitted:
 * - {@code gateway.requests.total}   — Counter tagged: method, route, status, outcome
 * - {@code gateway.request.duration} — Timer tagged:   method, route, status, outcome
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayMetricsFilter implements WebFilter {

    static final String METRIC_REQUESTS_TOTAL   = "gateway.requests.total";
    static final String METRIC_REQUEST_DURATION = "gateway.request.duration";

    private final MeterRegistry meterRegistry;

    public GatewayMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startNanos = System.nanoTime();
        return chain.filter(exchange)
                .then(Mono.defer(() -> recordMetrics(exchange, startNanos)))
                .onErrorResume(ex -> recordMetrics(exchange, startNanos).then(Mono.error(ex)));
    }

    private Mono<Void> recordMetrics(ServerWebExchange exchange, long startNanos) {
        return Mono.fromRunnable(() -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            int statusCode  = status != null ? status.value() : 0;
            String method   = exchange.getRequest().getMethod().name();
            String outcome  = AuditEvent.outcomeFor(statusCode);
            Route route     = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId  = route != null ? route.getId() : "unknown";

            Tags tags = Tags.of(
                    "method",  method,
                    "route",   routeId,
                    "status",  String.valueOf(statusCode),
                    "outcome", outcome
            );

            Counter.builder(METRIC_REQUESTS_TOTAL)
                    .description("Total gateway requests")
                    .tags(tags)
                    .register(meterRegistry)
                    .increment();

            Timer.builder(METRIC_REQUEST_DURATION)
                    .description("Gateway request duration in seconds")
                    .tags(tags)
                    .register(meterRegistry)
                    .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        });
    }
}
