package com.sentinelgateway.gateway.slowrequest;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * WebFilter that records requests exceeding the configured duration threshold (Phase 33).
 *
 * Runs at LOWEST_PRECEDENCE - 2 (after the chain), measures elapsed time via doFinally,
 * and records the request if duration > thresholdMs. Admin/actuator paths are excluded.
 */
@Component
public class SlowRequestFilter implements WebFilter, Ordered {

    public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 2;

    private final SlowRequestProperties properties;
    private final SlowRequestRegistry registry;

    public SlowRequestFilter(SlowRequestProperties properties, SlowRequestRegistry registry) {
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
        if (path.startsWith("/admin") || path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        long startMs = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();

        return chain.filter(exchange)
                .doFinally(signal -> {
                    long durationMs = System.currentTimeMillis() - startMs;
                    if (durationMs >= properties.getThresholdMs()) {
                        int status = exchange.getResponse().getStatusCode() != null
                                ? exchange.getResponse().getStatusCode().value()
                                : 0;
                        registry.record(new SlowRequestRecord(
                                Instant.now(), method, path, durationMs, status));
                    }
                });
    }
}
