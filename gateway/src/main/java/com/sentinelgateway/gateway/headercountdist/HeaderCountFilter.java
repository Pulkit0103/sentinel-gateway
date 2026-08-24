package com.sentinelgateway.gateway.headercountdist;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that tracks request header count distribution (Phase 58).
 *
 * Records the number of distinct header names in each request.
 * Skips /actuator/** and /admin/** traffic.
 * Runs at LOWEST_PRECEDENCE - 18.
 */
@Component
public class HeaderCountFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 18;

    private final HeaderCountProperties properties;
    private final HeaderCountRegistry registry;

    public HeaderCountFilter(HeaderCountProperties properties, HeaderCountRegistry registry) {
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

        int headerCount = exchange.getRequest().getHeaders().size();
        registry.record(headerCount);
        return chain.filter(exchange);
    }
}
