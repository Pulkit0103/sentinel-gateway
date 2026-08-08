package com.sentinelgateway.gateway.security;

import com.sentinelgateway.gateway.apikey.ApiKeyAuthentication;
import com.sentinelgateway.gateway.routing.RouteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enforces tenant isolation on routes flagged with {@code tenantRequired: true}.
 *
 * Tenant identity is extracted exclusively from the verified authentication
 * credential (JWT or API key) — never from caller-supplied headers. This prevents
 * tenant spoofing: an attacker cannot forge a different X-Tenant-Id by sending
 * an arbitrary header value.
 *
 * Policy:
 *   - Route not tenant-required → filter is a no-op
 *   - Route tenant-required + no tenant in credential → 403 (tenant context missing)
 *   - Route tenant-required + tenant present → allow (tenant is set by JwtHeadersFilter)
 *
 * Runs after RouteAuthorizationFilter (order HIGHEST_PRECEDENCE + 30).
 */
@Component
public class TenantIsolationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TenantIsolationFilter.class);

    private final RouteRegistry routeRegistry;
    private final JwtPrincipalExtractor principalExtractor;

    public TenantIsolationFilter(RouteRegistry routeRegistry, JwtPrincipalExtractor principalExtractor) {
        this.routeRegistry = routeRegistry;
        this.principalExtractor = principalExtractor;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route matchedRoute = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (matchedRoute == null) {
            return chain.filter(exchange);
        }

        var routeDef = routeRegistry.findById(matchedRoute.getId());
        if (routeDef.isEmpty() || !routeDef.get().tenantRequired()) {
            return chain.filter(exchange); // tenant not required on this route
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> extractPrincipal(auth))
                .map(principal -> principal.tenantId() != null && !principal.tenantId().isBlank())
                .defaultIfEmpty(false)
                .flatMap(hasTenant -> {
                    if (hasTenant) {
                        return chain.filter(exchange);
                    }
                    log.warn("Tenant required on route '{}' but no tenant in credential",
                            matchedRoute.getId());
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                });
    }

    private Mono<AuthenticatedPrincipal> extractPrincipal(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Mono.just(principalExtractor.extract(jwtAuth.getToken()));
        }
        if (auth instanceof ApiKeyAuthentication apiKeyAuth) {
            return Mono.just(apiKeyAuth.getPrincipal());
        }
        return Mono.empty();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30; // after RouteAuthorizationFilter (+20)
    }
}
