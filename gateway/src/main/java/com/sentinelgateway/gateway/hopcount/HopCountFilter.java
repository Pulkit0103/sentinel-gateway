package com.sentinelgateway.gateway.hopcount;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * WebFilter that detects and rejects requests with excessive proxy hops (Phase 44).
 *
 * Counts entries in the X-Forwarded-For header. Requests with more IPs than
 * {@code maxHops} are rejected with HTTP 400. Runs at HIGHEST_PRECEDENCE + 6 (very early).
 * All requests with a parseable X-Forwarded-For header are counted in the distribution.
 */
@Component
public class HopCountFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MIN_VALUE + 6;

    private final HopCountProperties properties;
    private final HopCountRegistry registry;

    public HopCountFilter(HopCountProperties properties, HopCountRegistry registry) {
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

        List<String> xForwardedFor = exchange.getRequest().getHeaders().get("X-Forwarded-For");
        int hopCount = countHops(xForwardedFor);

        if (hopCount > properties.getMaxHops()) {
            registry.recordRejected(hopCount);
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = String.format(
                    "{\"error\":\"Too many proxy hops\",\"maxHops\":%d,\"detectedHops\":%d}",
                    properties.getMaxHops(), hopCount);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        }

        registry.recordProcessed(hopCount);
        return chain.filter(exchange);
    }

    static int countHops(List<String> xForwardedFor) {
        if (xForwardedFor == null || xForwardedFor.isEmpty()) return 0;
        int count = 0;
        for (String header : xForwardedFor) {
            count += header.split(",").length;
        }
        return count;
    }
}
