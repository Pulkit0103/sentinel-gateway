package com.sentinelgateway.gateway.security;

import com.sentinelgateway.gateway.routing.RouteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enforces route-level RBAC by checking the caller's effective permissions
 * against the {@code requiredScopes} declared on each RouteDefinition.
 *
 * Effective permissions are the union of:
 *   1. Scopes present directly in the JWT {@code scope} / {@code scp} claim
 *   2. Permissions derived from the caller's roles via {@link RolePermissions}
 *
 * If the route declares no {@code requiredScopes}, all authenticated callers pass.
 * If the caller is missing any required permission, 403 Forbidden is returned.
 * Unauthenticated callers are blocked earlier by Spring Security (401).
 *
 * IMPLEMENTATION NOTE: never use switchIfEmpty() after a Mono<Void> pipeline
 * — Mono<Void> always completes "empty" so switchIfEmpty always fires, causing
 * chain.filter() to be called twice (double-subscription). Always build a
 * Mono<Boolean> via defaultIfEmpty() and then call chain.filter() exactly once.
 */
@Component
public class RouteAuthorizationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RouteAuthorizationFilter.class);

    private final RouteRegistry routeRegistry;
    private final JwtPrincipalExtractor principalExtractor;

    public RouteAuthorizationFilter(RouteRegistry routeRegistry, JwtPrincipalExtractor principalExtractor) {
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
        if (routeDef.isEmpty() || routeDef.get().requiredScopes().isEmpty()) {
            return chain.filter(exchange); // no permission requirements on this route
        }

        List<String> requiredScopes = routeDef.get().requiredScopes();

        // Build Mono<Boolean> = "is the caller authorized?"
        // defaultIfEmpty(false) handles the edge case where there's no JWT in context.
        // Then flatMap calls chain.filter() exactly once — avoids double-subscription.
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(principalExtractor::extract)
                .map(principal -> isAuthorized(principal, requiredScopes))
                .defaultIfEmpty(false)
                .flatMap(authorized -> {
                    if (authorized) {
                        return chain.filter(exchange);
                    }
                    log.warn("Route access denied on '{}' — required={}", matchedRoute.getId(), requiredScopes);
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                });
    }

    private boolean isAuthorized(AuthenticatedPrincipal principal, List<String> requiredScopes) {
        Set<String> effective = new HashSet<>(principal.scopes()); // direct JWT scopes
        effective.addAll(RolePermissions.effectivePermissionNames(principal.roles())); // role-derived
        return effective.containsAll(requiredScopes);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20; // after JwtHeadersFilter (+10)
    }
}
