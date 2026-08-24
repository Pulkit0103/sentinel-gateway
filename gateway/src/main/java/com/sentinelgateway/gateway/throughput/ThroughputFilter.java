package com.sentinelgateway.gateway.throughput;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that records a timestamp for each incoming request (Phase 66).
 *
 * Runs at LOWEST_PRECEDENCE - 25. Skips /actuator/** and /admin/**.
 */
@Component
public class ThroughputFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 25;

    private final ThroughputProperties properties;
    private final ThroughputRegistry registry;

    public ThroughputFilter(ThroughputProperties properties, ThroughputRegistry registry) {
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

        registry.record();
        return chain.filter(exchange);
    }
}
