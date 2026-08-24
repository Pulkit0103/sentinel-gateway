package com.sentinelgateway.gateway.queryparam;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that tracks request query-parameter count distribution (Phase 60).
 *
 * Runs at LOWEST_PRECEDENCE - 20. Skips /actuator/** and /admin/**.
 */
@Component
public class QueryParamFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 20;

    private final QueryParamProperties properties;
    private final QueryParamRegistry registry;

    public QueryParamFilter(QueryParamProperties properties, QueryParamRegistry registry) {
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

        int paramCount = exchange.getRequest().getQueryParams().size();
        registry.record(paramCount);

        return chain.filter(exchange);
    }
}
