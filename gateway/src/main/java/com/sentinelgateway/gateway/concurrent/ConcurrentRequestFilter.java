package com.sentinelgateway.gateway.concurrent;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that tracks the number of in-flight requests (Phase 41).
 *
 * Runs at HIGHEST_PRECEDENCE + 1 (very early) so it accurately captures concurrent
 * load including requests that are rejected by downstream filters (IP block, size guard, etc.).
 * Skips /actuator/** to avoid counting health probe traffic.
 */
@Component
public class ConcurrentRequestFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MIN_VALUE + 1;

    private final ConcurrentRequestProperties properties;
    private final ConcurrentRequestRegistry registry;

    public ConcurrentRequestFilter(ConcurrentRequestProperties properties,
                                   ConcurrentRequestRegistry registry) {
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
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        registry.increment();
        return chain.filter(exchange)
                .doFinally(signal -> registry.decrement());
    }
}
