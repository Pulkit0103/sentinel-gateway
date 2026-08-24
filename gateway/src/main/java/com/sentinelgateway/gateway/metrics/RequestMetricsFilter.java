package com.sentinelgateway.gateway.metrics;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that tracks inflight and completed request counts via
 * {@link RequestMetricsRegistry}.
 *
 * Order = HIGHEST_PRECEDENCE (wraps all other filters) so every request,
 * including those rejected by maintenance mode, IP filter, etc., is counted.
 *
 * Route ID is resolved after the chain completes so the route attribute set
 * by Spring Cloud Gateway route matching is available.
 */
@Component
public class RequestMetricsFilter implements WebFilter, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

    private final RequestMetricsRegistry registry;

    public RequestMetricsFilter(RequestMetricsRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        // Admin and actuator requests are internal operational traffic, not gateway traffic.
        if (path.startsWith("/admin") || path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        registry.requestStarted();
        return chain.filter(exchange)
                .doFinally(sig -> {
                    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    String routeId = route != null ? route.getId() : null;
                    registry.requestCompleted(routeId);
                });
    }
}
