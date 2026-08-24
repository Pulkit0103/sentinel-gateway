package com.sentinelgateway.gateway.routeerrorrate;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that tracks per-route error response rates in sliding windows (Phase 65).
 *
 * Uses beforeCommit to capture the final response status.
 * Route key is the first 2 path segments (same bucketing as LatencyFilter).
 * Runs at LOWEST_PRECEDENCE - 24. Skips /actuator/**.
 */
@Component
public class RouteErrorRateFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 24;

    private final RouteErrorRateProperties properties;
    private final RouteErrorRateRegistry registry;

    public RouteErrorRateFilter(RouteErrorRateProperties properties, RouteErrorRateRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    @Override
    public int getOrder() { return ORDER; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator") || path.startsWith("/admin")) {
            return chain.filter(exchange);
        }

        String route = pathBucket(path);

        exchange.getResponse().beforeCommit(() -> {
            org.springframework.http.HttpStatusCode sc = exchange.getResponse().getStatusCode();
            int status = sc != null ? sc.value() : 0;
            boolean isError = status >= 400;
            registry.record(route, isError);
            return Mono.empty();
        });

        return chain.filter(exchange);
    }

    static String pathBucket(String path) {
        if (path == null || path.isEmpty()) return "/";
        String[] parts = path.split("/", 4);
        if (parts.length < 3) return path;
        return "/" + parts[1] + "/" + parts[2];
    }
}
