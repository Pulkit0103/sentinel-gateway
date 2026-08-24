package com.sentinelgateway.gateway.routetraffic;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that records per-route request timestamps for windowed traffic analysis (Phase 53).
 *
 * Uses the same 2-segment path bucket as LatencyFilter (/api/users, /api/orders, etc.).
 * Skips /actuator/** and /admin/** traffic.
 * Runs at LOWEST_PRECEDENCE - 14.
 */
@Component
public class RouteTrafficFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 14;

    private final RouteTrafficProperties properties;
    private final RouteTrafficRegistry registry;

    public RouteTrafficFilter(RouteTrafficProperties properties, RouteTrafficRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator") || path.startsWith("/admin")) {
            return chain.filter(exchange);
        }

        registry.record(pathBucket(path));
        return chain.filter(exchange);
    }

    static String pathBucket(String path) {
        String[] parts = path.split("/", 4);
        if (parts.length <= 2) return path;
        return "/" + parts[1] + (parts.length > 2 && !parts[2].isEmpty() ? "/" + parts[2] : "");
    }
}
