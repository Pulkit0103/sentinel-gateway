package com.sentinelgateway.gateway.transform;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Removes implementation-leaking headers from upstream responses before sending to clients.
 *
 * Runs after the upstream response is received (LOWEST_PRECEDENCE - 10).
 * Uses the .then() callback so the response object is populated before header removal.
 */
@Component
public class ResponseHeadersFilter implements GlobalFilter, Ordered {

    private final TransformProperties transformProperties;

    public ResponseHeadersFilter(TransformProperties transformProperties) {
        this.transformProperties = transformProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            var responseHeaders = exchange.getResponse().getHeaders();
            transformProperties.getRemoveResponseHeaders().forEach(responseHeaders::remove);
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
