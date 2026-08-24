package com.sentinelgateway.gateway.ipcounter;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * WebFilter that counts requests per client IP address (Phase 49).
 *
 * Prefers the first value in X-Forwarded-For (the original client IP when behind a proxy).
 * Falls back to the remote address if the header is absent.
 * Skips /actuator/** and /admin/** traffic.
 * Runs at LOWEST_PRECEDENCE - 10.
 */
@Component
public class IpCounterFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 10;

    private final IpCounterProperties properties;
    private final IpCounterRegistry registry;

    public IpCounterFilter(IpCounterProperties properties, IpCounterRegistry registry) {
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

        registry.record(extractClientIp(exchange));
        return chain.filter(exchange);
    }

    static String extractClientIp(ServerWebExchange exchange) {
        List<String> xff = exchange.getRequest().getHeaders().get("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            String first = xff.get(0);
            int comma = first.indexOf(',');
            return (comma >= 0 ? first.substring(0, comma) : first).trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : "unknown";
    }
}
