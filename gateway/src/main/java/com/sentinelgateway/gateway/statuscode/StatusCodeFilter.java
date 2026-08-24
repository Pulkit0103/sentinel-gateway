package com.sentinelgateway.gateway.statuscode;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that records the HTTP response status code for every request (Phase 40).
 *
 * Runs at LOWEST_PRECEDENCE - 5 (after all business filters). Uses doFinally to
 * capture the status code regardless of success or error. Skips /actuator/** to
 * avoid polluting metrics with health-probe traffic.
 */
@Component
public class StatusCodeFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 5;

    private final StatusCodeRegistry registry;
    private final StatusCodeProperties properties;

    public StatusCodeFilter(StatusCodeRegistry registry, StatusCodeProperties properties) {
        this.registry = registry;
        this.properties = properties;
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

        return chain.filter(exchange)
                .doFinally(signal -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    if (status != null) {
                        registry.record(status.value());
                    }
                });
    }
}
