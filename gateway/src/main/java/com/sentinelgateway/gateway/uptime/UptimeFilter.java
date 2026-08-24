package com.sentinelgateway.gateway.uptime;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that increments the request counter in {@link UptimeRegistry} for every request (Phase 30).
 *
 * Runs at HIGHEST_PRECEDENCE + 1 to count all inbound requests before any filtering logic.
 * Admin and actuator paths are excluded so operational requests don't inflate metrics.
 */
@Component
public class UptimeFilter implements WebFilter, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 1;

    private final UptimeRegistry registry;

    public UptimeFilter(UptimeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/admin") && !path.startsWith("/actuator")) {
            registry.recordRequest();
        }
        return chain.filter(exchange);
    }
}
