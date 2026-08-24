package com.sentinelgateway.gateway.latency;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that measures end-to-end request latency and records it per route (Phase 38).
 *
 * Runs at LOWEST_PRECEDENCE - 4 (after all business filters) so it captures total gateway time.
 * Skips /admin/** and /actuator/** to keep samples focused on API traffic.
 * Uses doFinally so the sample is always recorded regardless of success or error.
 */
@Component
public class LatencyFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 4;

    private final LatencyProperties properties;
    private final LatencyRegistry registry;

    public LatencyFilter(LatencyProperties properties, LatencyRegistry registry) {
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
        if (path.startsWith("/admin") || path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        long startMs = System.currentTimeMillis();
        return chain.filter(exchange)
                .doFinally(signal -> {
                    long durationMs = System.currentTimeMillis() - startMs;
                    registry.record(pathBucket(path), durationMs);
                });
    }

    static String pathBucket(String path) {
        String[] parts = path.split("/", 4);
        if (parts.length <= 2) return path;
        return "/" + parts[1] + (parts.length > 2 && !parts[2].isEmpty() ? "/" + parts[2] : "");
    }
}
