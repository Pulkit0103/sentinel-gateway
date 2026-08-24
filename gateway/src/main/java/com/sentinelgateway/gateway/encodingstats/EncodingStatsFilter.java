package com.sentinelgateway.gateway.encodingstats;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that records request Content-Encoding header distribution (Phase 54).
 *
 * Reads Content-Encoding at request entry time; records "none" when the header is absent.
 * Skips /actuator/** and /admin/** traffic.
 * Runs at LOWEST_PRECEDENCE - 15.
 */
@Component
public class EncodingStatsFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 15;

    private final EncodingStatsProperties properties;
    private final EncodingStatsRegistry registry;

    public EncodingStatsFilter(EncodingStatsProperties properties, EncodingStatsRegistry registry) {
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

        String encoding = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
        registry.record(encoding);
        return chain.filter(exchange);
    }
}
