package com.sentinelgateway.gateway.responsesize;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that records response Content-Length for bandwidth monitoring (Phase 42).
 *
 * Uses beforeCommit to read the Content-Length header just before the response is flushed.
 * Responses without a Content-Length (chunked streaming) are silently skipped.
 * Runs at LOWEST_PRECEDENCE - 6 (after all business filters).
 * Skips /actuator/** health probe traffic.
 */
@Component
public class ResponseSizeFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 6;

    private final ResponseSizeProperties properties;
    private final ResponseSizeRegistry registry;

    public ResponseSizeFilter(ResponseSizeProperties properties, ResponseSizeRegistry registry) {
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
            long contentLength = exchange.getResponse().getHeaders().getContentLength();
            if (contentLength >= 0) {
                registry.record(contentLength);
            }
            return Mono.empty();
        });

        return chain.filter(exchange);
    }
}
