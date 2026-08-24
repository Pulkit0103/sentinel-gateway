package com.sentinelgateway.gateway.pathlength;

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
 * WebFilter that rejects requests with excessively long paths or query strings (Phase 43).
 *
 * Returns HTTP 414 URI Too Long for path violations and HTTP 400 for query string violations.
 * Runs at HIGHEST_PRECEDENCE + 5 (very early, before sanitizer and security).
 * Rejection is recorded in {@link PathLengthRegistry} for audit.
 */
@Component
public class PathLengthFilter implements WebFilter, Ordered {

    public static final int ORDER = Integer.MIN_VALUE + 5;

    private final PathLengthProperties properties;
    private final PathLengthRegistry registry;

    public PathLengthFilter(PathLengthProperties properties, PathLengthRegistry registry) {
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
        String rawQuery = exchange.getRequest().getURI().getRawQuery();
        int pathLen = path.length();
        int queryLen = rawQuery != null ? rawQuery.length() : 0;

        String method = exchange.getRequest().getMethod().name();

        if (pathLen > properties.getMaxPathLength()) {
            registry.record(new PathLengthRecord(
                    Instant.now(), method, truncate(path, 200), pathLen, queryLen, "path"));
            return reject(exchange, HttpStatus.URI_TOO_LONG,
                    String.format("{\"error\":\"URI path too long\",\"maxLength\":%d,\"actualLength\":%d}",
                            properties.getMaxPathLength(), pathLen));
        }

        if (queryLen > properties.getMaxQueryLength()) {
            registry.record(new PathLengthRecord(
                    Instant.now(), method, path, pathLen, queryLen, "query"));
            return reject(exchange, HttpStatus.BAD_REQUEST,
                    String.format("{\"error\":\"Query string too long\",\"maxLength\":%d,\"actualLength\":%d}",
                            properties.getMaxQueryLength(), queryLen));
        }

        return chain.filter(exchange);
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String json) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
