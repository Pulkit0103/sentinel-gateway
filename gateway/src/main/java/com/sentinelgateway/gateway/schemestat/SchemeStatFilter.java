package com.sentinelgateway.gateway.schemestat;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that tracks request URI scheme distribution (Phase 61).
 *
 * Runs at LOWEST_PRECEDENCE - 21. Skips /actuator/** and /admin/**.
 * Falls back to X-Forwarded-Proto when the request URI scheme is http
 * (common behind TLS-terminating load balancers).
 */
@Component
public class SchemeStatFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 21;

    private final SchemeStatProperties properties;
    private final SchemeStatRegistry registry;

    public SchemeStatFilter(SchemeStatProperties properties, SchemeStatRegistry registry) {
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

        String scheme = resolveScheme(exchange);
        registry.record(scheme);

        return chain.filter(exchange);
    }

    static String resolveScheme(ServerWebExchange exchange) {
        // X-Forwarded-Proto is more reliable when behind a TLS-terminating proxy
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.trim().toLowerCase();
        }
        String scheme = exchange.getRequest().getURI().getScheme();
        return scheme != null ? scheme.toLowerCase() : "unknown";
    }
}
