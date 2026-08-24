package com.sentinelgateway.gateway.errorpath;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * WebFilter that records 4xx and 5xx error responses to an in-memory ring-buffer (Phase 56).
 *
 * Uses beforeCommit to read the response status code before flush.
 * Only records responses with status >= 400.
 * Skips /actuator/** traffic; includes /admin/** to catch admin errors too.
 * Runs at LOWEST_PRECEDENCE - 16.
 */
@Component
public class ErrorPathFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 16;

    private final ErrorPathProperties properties;
    private final ErrorPathRegistry registry;

    public ErrorPathFilter(ErrorPathProperties properties, ErrorPathRegistry registry) {
        this.properties = properties;
        this.registry = registry;
        registry.setMaxRecords(properties.getMaxRecords());
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
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        exchange.getResponse().beforeCommit(() -> {
            HttpStatus status = exchange.getResponse().getStatusCode() instanceof HttpStatus hs ? hs : null;
            int code = status != null ? status.value() : (exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 0);
            if (code >= 400) {
                registry.record(new ErrorPathRecord(
                        Instant.now(),
                        exchange.getRequest().getMethod().name(),
                        exchange.getRequest().getPath().value(),
                        code
                ));
            }
            return Mono.empty();
        });

        return chain.filter(exchange);
    }
}
