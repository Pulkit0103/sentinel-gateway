package com.sentinelgateway.gateway.pathdepth;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that tracks URL path-depth distribution (Phase 62).
 *
 * Runs at LOWEST_PRECEDENCE - 22. Skips /actuator/** and /admin/**.
 */
@Component
public class PathDepthFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 22;

    private final PathDepthProperties properties;
    private final PathDepthRegistry registry;

    public PathDepthFilter(PathDepthProperties properties, PathDepthRegistry registry) {
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

        int depth = PathDepthRegistry.countSegments(path);
        registry.record(depth);

        return chain.filter(exchange);
    }
}
