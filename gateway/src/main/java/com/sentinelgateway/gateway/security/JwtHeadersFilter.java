package com.sentinelgateway.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Propagates verified JWT identity claims as trusted headers to upstream services.
 *
 * Runs after Spring Security has validated the JWT; the principal fields are
 * extracted by {@link JwtPrincipalExtractor} from the verified token only.
 * Upstream services must accept these headers ONLY from the gateway — never
 * from external callers — to prevent identity spoofing.
 *
 * Headers propagated:
 *   X-User-Id    — JWT {@code sub} claim
 *   X-Tenant-Id  — custom {@code tenant_id} claim (if present)
 *   X-User-Roles — comma-separated roles (realm_access.roles or roles claim)
 *
 * IMPLEMENTATION NOTE: do NOT use switchIfEmpty() after the flatMap chain.
 * chain.filter() returns Mono<Void> which always completes "empty" (no item
 * emitted), so switchIfEmpty() would fire a second chain.filter() call, causing
 * double-subscription on the response body ("Rejecting additional inbound
 * receiver"). Instead, build a Mono<ServerWebExchange> first and then call
 * chain.filter() exactly once via flatMap.
 */
@Component
public class JwtHeadersFilter implements GlobalFilter, Ordered {

    private final JwtPrincipalExtractor principalExtractor;

    public JwtHeadersFilter(JwtPrincipalExtractor principalExtractor) {
        this.principalExtractor = principalExtractor;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(principalExtractor::extract)
                .map(principal -> {
                    ServerHttpRequest.Builder req = exchange.getRequest().mutate()
                            .header("X-User-Id", principal.userId());

                    if (principal.tenantId() != null && !principal.tenantId().isBlank()) {
                        req.header("X-Tenant-Id", principal.tenantId());
                    }
                    if (!principal.roles().isEmpty()) {
                        req.header("X-User-Roles", String.join(",", principal.roles()));
                    }

                    return (ServerWebExchange) exchange.mutate().request(req.build()).build();
                })
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
