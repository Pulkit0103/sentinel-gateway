package com.sentinelgateway.gateway.useragent;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that records request User-Agent category distribution (Phase 48).
 *
 * Reads the User-Agent header at request entry time.
 * Skips /actuator/** health probe traffic.
 * Runs at LOWEST_PRECEDENCE - 9.
 */
@Component
public class UserAgentFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MAX_VALUE - 9;

    private final UserAgentProperties properties;
    private final UserAgentRegistry registry;

    public UserAgentFilter(UserAgentProperties properties, UserAgentRegistry registry) {
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

        String ua = exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
        registry.record(ua);
        return chain.filter(exchange);
    }
}
