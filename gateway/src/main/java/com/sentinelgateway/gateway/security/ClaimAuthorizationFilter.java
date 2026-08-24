package com.sentinelgateway.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * GlobalFilter that enforces per-route JWT claim authorization requirements.
 *
 * For each configured route, checks that the authenticated JWT contains each required
 * claim with one of the allowed values. All requirements must be satisfied (AND logic).
 *
 * Non-JWT authentication and routes with no requirements pass through unchanged.
 * Returns 403 Forbidden when any requirement fails.
 *
 * Runs at {@code Ordered.HIGHEST_PRECEDENCE + 6} — after route matching.
 */
@Component
public class ClaimAuthorizationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ClaimAuthorizationFilter.class);

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 6;

    private final ClaimAuthorizationProperties properties;

    public ClaimAuthorizationFilter(ClaimAuthorizationProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) return chain.filter(exchange);

        List<ClaimAuthorizationProperties.ClaimRequirement> requirements =
                properties.requirementsForRoute(route.getId());
        if (requirements.isEmpty()) return chain.filter(exchange);

        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> {
                    if (!(ctx.getAuthentication() instanceof JwtAuthenticationToken jwtAuth)) {
                        return true; // non-JWT: skip claim checks
                    }
                    for (ClaimAuthorizationProperties.ClaimRequirement req : requirements) {
                        Object claimValue = jwtAuth.getToken().getClaim(req.getClaim());
                        String strValue = claimValue != null ? claimValue.toString() : null;
                        if (strValue == null || !req.getAllowedValues().contains(strValue)) {
                            log.warn("Route {} claim authorization failed: claim={} value={} allowed={}",
                                    route.getId(), req.getClaim(), strValue, req.getAllowedValues());
                            return false;
                        }
                    }
                    return true;
                })
                .defaultIfEmpty(true)
                .flatMap(allowed -> allowed ? chain.filter(exchange) : sendForbidden(exchange));
    }

    private Mono<Void> sendForbidden(ServerWebExchange exchange) {
        String body = "{\"status\":403,\"error\":\"JWT claim authorization failed\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(bytes.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
