package com.sentinelgateway.gateway.responseheadersize;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that tracks response header total-size distribution (Phase 59).
 *
 * Uses beforeCommit to measure all response header bytes just before flush.
 * Skips /actuator/** and /admin/** traffic.
 * Runs at LOWEST_PRECEDENCE - 19.
 */
@Component
public class ResponseHeaderSizeFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 19;

    private final ResponseHeaderSizeProperties properties;
    private final ResponseHeaderSizeRegistry registry;

    public ResponseHeaderSizeFilter(ResponseHeaderSizeProperties properties, ResponseHeaderSizeRegistry registry) {
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

        exchange.getResponse().beforeCommit(() -> {
            long size = ResponseHeaderSizeRegistry.measureHeaders(exchange.getResponse().getHeaders());
            registry.record(size);
            return Mono.empty();
        });

        return chain.filter(exchange);
    }
}
