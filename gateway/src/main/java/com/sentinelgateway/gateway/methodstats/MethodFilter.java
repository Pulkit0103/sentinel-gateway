package com.sentinelgateway.gateway.methodstats;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that counts requests by HTTP method (Phase 46).
 *
 * Runs at LOWEST_PRECEDENCE - 7. Skips /actuator/** health probe traffic.
 */
@Component
public class MethodFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 7;

    private final MethodProperties properties;
    private final MethodRegistry registry;

    public MethodFilter(MethodProperties properties, MethodRegistry registry) {
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

        registry.record(exchange.getRequest().getMethod().name());
        return chain.filter(exchange);
    }
}
