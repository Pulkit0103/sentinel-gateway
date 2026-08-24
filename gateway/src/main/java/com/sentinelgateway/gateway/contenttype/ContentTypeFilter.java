package com.sentinelgateway.gateway.contenttype;

import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that records response Content-Type distribution (Phase 47).
 *
 * Uses beforeCommit to read the Content-Type header just before the response is flushed.
 * Skips /actuator/** and /admin/** traffic.
 * Runs at LOWEST_PRECEDENCE - 8.
 */
@Component
public class ContentTypeFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 8;

    private final ContentTypeProperties properties;
    private final ContentTypeRegistry registry;

    public ContentTypeFilter(ContentTypeProperties properties, ContentTypeRegistry registry) {
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
            MediaType ct = exchange.getResponse().getHeaders().getContentType();
            registry.record(ct != null ? ct.toString() : null);
            return Mono.empty();
        });

        return chain.filter(exchange);
    }
}
