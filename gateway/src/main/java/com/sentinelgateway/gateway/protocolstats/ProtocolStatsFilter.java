package com.sentinelgateway.gateway.protocolstats;

import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * WebFilter that records request Accept header distribution (Phase 52).
 *
 * Normalises the Accept header to its primary media type (strips parameters, weights).
 * Skips /actuator/** and /admin/** traffic.
 * Runs at LOWEST_PRECEDENCE - 13.
 */
@Component
public class ProtocolStatsFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 13;

    private final ProtocolStatsProperties properties;
    private final ProtocolStatsRegistry registry;

    public ProtocolStatsFilter(ProtocolStatsProperties properties, ProtocolStatsRegistry registry) {
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

        List<MediaType> acceptTypes = exchange.getRequest().getHeaders().getAccept();
        String key = acceptTypes.isEmpty() ? "not-set" : normalise(acceptTypes.get(0).toString());
        registry.record(key);
        return chain.filter(exchange);
    }

    static String normalise(String accept) {
        if (accept == null || accept.isBlank()) return "not-set";
        int semi = accept.indexOf(';');
        return (semi >= 0 ? accept.substring(0, semi) : accept).trim().toLowerCase();
    }
}
