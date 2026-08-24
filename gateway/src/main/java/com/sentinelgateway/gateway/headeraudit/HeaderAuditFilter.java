package com.sentinelgateway.gateway.headeraudit;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that audits response header presence for security/cache-control headers (Phase 51).
 *
 * Uses beforeCommit to inspect the response headers just before flush.
 * Skips /actuator/** and /admin/** traffic.
 * Runs at LOWEST_PRECEDENCE - 12.
 */
@Component
public class HeaderAuditFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 12;

    private final HeaderAuditProperties properties;
    private final HeaderAuditRegistry registry;

    public HeaderAuditFilter(HeaderAuditProperties properties, HeaderAuditRegistry registry) {
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
            registry.incrementResponses();
            for (String header : properties.getTrackedHeaders()) {
                boolean present = exchange.getResponse().getHeaders().containsKey(header);
                registry.record(header, present);
            }
            return Mono.empty();
        });

        return chain.filter(exchange);
    }
}
