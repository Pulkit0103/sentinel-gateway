package com.sentinelgateway.gateway.session;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * GlobalFilter that records JWT subject activity in {@link ActiveSessionRegistry}.
 *
 * Runs at {@code Ordered.LOWEST_PRECEDENCE - 1} (near the end) after authentication
 * is fully resolved. Only records JWT-authenticated requests; API keys and unauthenticated
 * requests are skipped.
 */
@Component
public class SessionTrackingFilter implements GlobalFilter, Ordered {

    public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 1;

    private final ActiveSessionRegistry registry;

    public SessionTrackingFilter(ActiveSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .then(ReactiveSecurityContextHolder.getContext()
                        .doOnNext(ctx -> {
                            if (ctx.getAuthentication() instanceof JwtAuthenticationToken jwtAuth) {
                                String subject = jwtAuth.getToken().getSubject();
                                registry.recordActivity(subject);
                            }
                        })
                        .then());
    }
}
