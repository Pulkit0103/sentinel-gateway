package com.sentinelgateway.gateway.authstat;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class AuthStatFilter implements WebFilter, Ordered {

    static final int ORDER = Integer.MAX_VALUE - 26;

    private final AuthStatProperties properties;
    private final AuthStatRegistry registry;

    public AuthStatFilter(AuthStatProperties properties, AuthStatRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    @Override
    public int getOrder() { return ORDER; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isEnabled()) return chain.filter(exchange);
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator") || path.startsWith("/admin")) return chain.filter(exchange);

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        registry.record(authHeader);
        return chain.filter(exchange);
    }
}
