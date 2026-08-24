package com.sentinelgateway.gateway.errorrate;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that tracks HTTP error rates per request path (Phase 35).
 *
 * Runs at LOWEST_PRECEDENCE - 3, after the chain. Uses doFinally to capture
 * the response status code. Admin and actuator paths are excluded.
 *
 * Path is truncated to the first two segments (e.g., /api/users/123 → /api/users)
 * to group requests by logical endpoint rather than individual resource IDs.
 */
@Component
public class ErrorRateFilter implements WebFilter, Ordered {

    public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 3;

    private final ErrorRateRegistry registry;

    public ErrorRateFilter(ErrorRateRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/admin") || path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String bucket = pathBucket(path);
        return chain.filter(exchange)
                .doFinally(signal -> {
                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                    if (statusCode != null) {
                        registry.record(bucket, statusCode.value());
                    }
                });
    }

    /** Groups path to first two segments: /api/users/123 → /api/users */
    static String pathBucket(String path) {
        String[] parts = path.split("/", 4);
        if (parts.length <= 2) return path;
        return "/" + parts[1] + (parts.length > 2 && !parts[2].isEmpty() ? "/" + parts[2] : "");
    }
}
