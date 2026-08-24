package com.sentinelgateway.gateway.sla;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * GlobalFilter that records per-route response times for SLA tracking.
 *
 * Runs at {@code Ordered.LOWEST_PRECEDENCE} (last) so it measures the full
 * round-trip including all other filters and upstream latency.
 *
 * Is a no-op when {@code sentinel.sla.enabled=false} or when the matched
 * route has no configured SLA target.
 */
@Component
public class SlaTrackingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SlaTrackingFilter.class);

    public static final int ORDER = Ordered.LOWEST_PRECEDENCE;

    private final SlaProperties properties;
    private final SlaTracker tracker;

    public SlaTrackingFilter(SlaProperties properties, SlaTracker tracker) {
        this.properties = properties;
        this.tracker = tracker;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        long start = System.currentTimeMillis();
        return chain.filter(exchange)
                .doFinally(sig -> {
                    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    if (route == null || !properties.hasSla(route.getId())) return;

                    long duration = System.currentTimeMillis() - start;
                    long target = properties.targetMs(route.getId());
                    tracker.record(route.getId(), duration, target);

                    if (duration > target) {
                        log.warn("SLA breach on route {}: {}ms > {}ms target", route.getId(), duration, target);
                    }
                });
    }
}
