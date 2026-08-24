package com.sentinelgateway.gateway.security;

import com.sentinelgateway.gateway.apikey.ApiKeyAuthentication;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Propagates verified identity claims as trusted headers to upstream services.
 *
 * Supports both JWT (JwtAuthenticationToken) and API key (ApiKeyAuthentication) auth.
 * Headers are always derived from the verified credential — never from raw request headers.
 *
 * Headers propagated:
 *   X-User-Id    — userId from authenticated principal
 *   X-Tenant-Id  — tenantId (if present)
 *   X-User-Roles — comma-separated roles (if any)
 *
 * REACTOR SAFETY: uses defaultIfEmpty(exchange).flatMap(chain::filter)
 * not switchIfEmpty — Mono<Void> always completes empty, so switchIfEmpty would
 * call chain.filter() twice, causing double-subscription on the response body.
 */
@Component
public class JwtHeadersFilter implements GlobalFilter, Ordered {

    private final JwtPrincipalExtractor principalExtractor;
    private final JwtClaimsForwardingProperties claimsForwardingProperties;

    public JwtHeadersFilter(JwtPrincipalExtractor principalExtractor,
                             JwtClaimsForwardingProperties claimsForwardingProperties) {
        this.principalExtractor = principalExtractor;
        this.claimsForwardingProperties = claimsForwardingProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> extractPrincipal(auth, principalExtractor)
                        .map(principal -> {
                            ServerHttpRequest.Builder req = exchange.getRequest().mutate()
                                    .header("X-User-Id", principal.userId());

                            if (principal.tenantId() != null && !principal.tenantId().isBlank()) {
                                req.header("X-Tenant-Id", principal.tenantId());
                            }
                            if (!principal.roles().isEmpty()) {
                                req.header("X-User-Roles", String.join(",", principal.roles()));
                            }

                            // Extra configurable claims forwarding (opt-in via sentinel.jwt.claims-forwarding.enabled)
                            if (claimsForwardingProperties.isEnabled() && auth instanceof JwtAuthenticationToken jwtAuth) {
                                var claims = jwtAuth.getToken().getClaims();
                                for (var mapping : claimsForwardingProperties.getMappings()) {
                                    Object val = claims.get(mapping.getClaim());
                                    if (val instanceof String s && !s.isBlank()) {
                                        req.header(mapping.getHeader(), s);
                                    }
                                }
                            }

                            return (ServerWebExchange) exchange.mutate().request(req.build()).build();
                        })
                )
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    static Mono<AuthenticatedPrincipal> extractPrincipal(Authentication auth,
                                                          JwtPrincipalExtractor extractor) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Mono.just(extractor.extract(jwtAuth.getToken()));
        }
        if (auth instanceof ApiKeyAuthentication apiKeyAuth) {
            return Mono.just(apiKeyAuth.getPrincipal());
        }
        return Mono.empty();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
