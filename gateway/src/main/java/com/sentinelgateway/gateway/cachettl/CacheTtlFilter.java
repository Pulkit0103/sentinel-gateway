package com.sentinelgateway.gateway.cachettl;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that tracks response Cache-Control TTL bucket distribution (Phase 57).
 *
 * Uses beforeCommit to read the Cache-Control response header before flush.
 * Skips /actuator/** and /admin/** traffic.
 * Runs at LOWEST_PRECEDENCE - 17.
 */
@Component
public class CacheTtlFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 17;

    private final CacheTtlProperties properties;
    private final CacheTtlRegistry registry;

    public CacheTtlFilter(CacheTtlProperties properties, CacheTtlRegistry registry) {
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
            String cc = exchange.getResponse().getHeaders().getFirst(HttpHeaders.CACHE_CONTROL);
            registry.record(cc);
            return Mono.empty();
        });

        return chain.filter(exchange);
    }
}
