package com.sentinelgateway.gateway.routeblock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * GlobalFilter that returns 503 for routes registered in {@link RouteBlockRegistry}.
 *
 * Runs at {@code Ordered.HIGHEST_PRECEDENCE + 3} — after IP filtering (+1) and
 * maintenance (+2) but before header validation (+5) and JWT revocation (+5).
 *
 * Unlike disabling a route (which removes it from routing and returns 404),
 * blocking a route keeps routing intact but gates traffic with a 503, giving
 * operators a reversible emergency stop without configuration changes.
 */
@Component
public class RouteBlockFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RouteBlockFilter.class);

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 3;

    private final RouteBlockRegistry registry;

    public RouteBlockFilter(RouteBlockRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null || !registry.isBlocked(route.getId())) {
            return chain.filter(exchange);
        }

        log.warn("Route {} is administratively blocked — returning 503", route.getId());
        return sendBlockedResponse(exchange, route.getId());
    }

    private Mono<Void> sendBlockedResponse(ServerWebExchange exchange, String routeId) {
        String body = String.format(
                "{\"status\":503,\"error\":\"Route temporarily blocked\",\"routeId\":\"%s\"}", routeId);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(bytes.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
