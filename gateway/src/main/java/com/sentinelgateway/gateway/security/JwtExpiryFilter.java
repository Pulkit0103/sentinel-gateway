package com.sentinelgateway.gateway.security;

import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * WebFilter that adds an expiry warning header when the caller's JWT is about to expire (Phase 36).
 *
 * If the JWT's {@code exp} claim is within {@code warningWindowSeconds} of now, the response
 * receives a header {@code X-JWT-Expires-In: <seconds>} indicating seconds remaining.
 *
 * Runs at ORDER = 1 (after authentication but early in the response pipeline).
 * Non-JWT requests are silently skipped.
 */
@Component
public class JwtExpiryFilter implements WebFilter, Ordered {

    public static final int ORDER = 1;

    private final JwtExpiryProperties properties;

    public JwtExpiryFilter(JwtExpiryProperties properties) {
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

        exchange.getResponse().beforeCommit(() ->
                ReactiveSecurityContextHolder.getContext()
                        .doOnNext(ctx -> {
                            if (!(ctx.getAuthentication() instanceof JwtAuthenticationToken jwtAuth)) return;
                            Instant exp = jwtAuth.getToken().getExpiresAt();
                            if (exp == null) return;
                            long secondsRemaining = exp.getEpochSecond() - Instant.now().getEpochSecond();
                            if (secondsRemaining <= properties.getWarningWindowSeconds()) {
                                exchange.getResponse().getHeaders()
                                        .set(properties.getHeaderName(), String.valueOf(secondsRemaining));
                            }
                        })
                        .then());

        return chain.filter(exchange);
    }
}
