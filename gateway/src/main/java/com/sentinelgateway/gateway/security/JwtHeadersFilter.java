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
 * Runs only when the security context contains a validated JwtAuthenticationToken —
 * i.e., after Spring Security has already verified the signature, expiry, and issuer.
 * Upstream services must never trust these headers from external callers; they are
 * stripped and re-set here from the validated token only.
 *
 * Headers added:
 *   X-User-Id    — JWT `sub` claim (canonical user identity)
 *   X-Tenant-Id  — custom `tenant_id` claim (if present)
 */
@Component
public class JwtHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Build a Mono<ServerWebExchange>: either mutated (JWT present) or original.
        // Then call chain.filter() exactly once on whichever exchange is resolved.
        // NOTE: do NOT use switchIfEmpty() after a Mono<Void> — Mono<Void> always
        // completes "empty" (no item emitted), so switchIfEmpty() would fire a second
        // chain.filter() call, causing double-subscription on the response body.
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(jwt -> {
                    ServerHttpRequest.Builder req = exchange.getRequest().mutate()
                            .header("X-User-Id", jwt.getSubject());
                    String tenantId = jwt.getClaimAsString("tenant_id");
                    if (tenantId != null && !tenantId.isBlank()) {
                        req.header("X-Tenant-Id", tenantId);
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
