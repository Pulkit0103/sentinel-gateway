package com.sentinelgateway.gateway.security;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * WebFilter that force-sets OWASP-recommended security headers on every response (Phase 29).
 *
 * Uses {@code ServerHttpResponse.beforeCommit} to apply headers immediately before the
 * response is flushed, overriding any previous values (including Spring Security defaults).
 *
 * Runs at LOWEST_PRECEDENCE so it registers last and runs its beforeCommit callback
 * after all upstream handlers have set their own headers.
 */
@Component
public class SecurityHeadersFilter implements WebFilter, Ordered {

    public static final int ORDER = Ordered.LOWEST_PRECEDENCE;

    private final SecurityHeadersProperties properties;

    public SecurityHeadersFilter(SecurityHeadersProperties properties) {
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
        // Register a beforeCommit hook so we can set headers right before the response flushes.
        // This ensures we run after Spring Security (and any handler) has set its own headers.
        exchange.getResponse().beforeCommit(() -> {
            Map<String, String> effective = properties.effectiveHeaders();
            effective.forEach((name, value) ->
                    exchange.getResponse().getHeaders().set(name, value));
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
