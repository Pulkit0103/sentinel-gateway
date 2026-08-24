package com.sentinelgateway.gateway.clockskew;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * WebFilter that detects requests with a client-supplied timestamp that deviates
 * significantly from server time (Phase 50).
 *
 * Reads X-Request-Timestamp (epoch seconds), computes |server_time - client_time|,
 * and records any request where skew exceeds toleranceSeconds.
 * Does NOT block — purely observational.
 * Skips /actuator/** and /admin/** traffic.
 * Runs at LOWEST_PRECEDENCE - 11.
 */
@Component
public class ClockSkewFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 11;

    private final ClockSkewProperties properties;
    private final ClockSkewRegistry registry;

    public ClockSkewFilter(ClockSkewProperties properties, ClockSkewRegistry registry) {
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
        if (path.startsWith("/actuator") || path.startsWith("/admin")) {
            return chain.filter(exchange);
        }

        String tsHeader = exchange.getRequest().getHeaders().getFirst(properties.getHeaderName());
        if (tsHeader != null && !tsHeader.isBlank()) {
            try {
                long clientTs = Long.parseLong(tsHeader.trim());
                long serverTs = Instant.now().getEpochSecond();
                long skew = Math.abs(serverTs - clientTs);
                registry.recordChecked();
                if (skew > properties.getToleranceSeconds()) {
                    registry.recordSkewed(new SkewedRequestRecord(
                            Instant.now(),
                            exchange.getRequest().getMethod().name(),
                            path,
                            clientTs,
                            skew
                    ));
                }
            } catch (NumberFormatException ignored) {
                // Malformed timestamp — skip silently
            }
        }

        return chain.filter(exchange);
    }
}
