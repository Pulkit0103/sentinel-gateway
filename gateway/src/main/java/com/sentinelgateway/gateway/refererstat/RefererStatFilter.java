package com.sentinelgateway.gateway.refererstat;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that tracks Referer header domain distribution (Phase 63).
 *
 * Runs at LOWEST_PRECEDENCE - 23. Skips /actuator/** and /admin/**.
 */
@Component
public class RefererStatFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 23;

    private final RefererStatProperties properties;
    private final RefererStatRegistry registry;

    public RefererStatFilter(RefererStatProperties properties, RefererStatRegistry registry) {
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

        String referer = exchange.getRequest().getHeaders().getFirst("Referer");
        registry.record(referer);

        return chain.filter(exchange);
    }
}
