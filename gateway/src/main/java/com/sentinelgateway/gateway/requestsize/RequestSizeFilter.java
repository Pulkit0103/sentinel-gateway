package com.sentinelgateway.gateway.requestsize;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * WebFilter that rejects requests whose Content-Length header exceeds the configured limit (Phase 37).
 *
 * Runs at HIGHEST_PRECEDENCE + 4 — before the request sanitizer — so oversized payloads
 * are blocked before any header inspection. Requests without a Content-Length header
 * (e.g. chunked uploads) are passed through; only explicitly declared sizes are checked.
 */
@Component
public class RequestSizeFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MIN_VALUE + 4;

    private final RequestSizeProperties properties;
    private final OversizedRequestRegistry registry;

    public RequestSizeFilter(RequestSizeProperties properties, OversizedRequestRegistry registry) {
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

        long contentLength = exchange.getRequest().getHeaders().getContentLength();
        if (contentLength > properties.getMaxBodyBytes()) {
            registry.record(new OversizedRequestRecord(
                    Instant.now(),
                    exchange.getRequest().getMethod().name(),
                    exchange.getRequest().getPath().value(),
                    contentLength
            ));

            exchange.getResponse().setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = String.format(
                    "{\"error\":\"Request body too large\",\"maxBytes\":%d,\"claimedBytes\":%d}",
                    properties.getMaxBodyBytes(), contentLength);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        }

        return chain.filter(exchange);
    }
}
