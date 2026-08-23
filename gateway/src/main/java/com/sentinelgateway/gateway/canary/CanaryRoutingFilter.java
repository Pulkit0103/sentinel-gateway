package com.sentinelgateway.gateway.canary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Global filter that implements percentage-based canary routing.
 *
 * <p>For each incoming request the filter checks whether the matched route has a
 * canary configuration in {@link CanaryProperties}. When it does, a random integer
 * in [1, 100] is drawn; if it is ≤ the configured {@code weight} the request is
 * redirected to the canary backend by replacing the
 * {@link ServerWebExchangeUtils#GATEWAY_REQUEST_URL_ATTR} attribute that the
 * built-in {@code NettyRoutingFilter} reads when it dispatches the HTTP call.
 *
 * <p>Running at {@link Ordered#LOWEST_PRECEDENCE} − 100 ensures the filter executes
 * after route matching (which sets {@code GATEWAY_ROUTE_ATTR}) but before the
 * Netty routing filter that actually makes the upstream call.
 */
@Component
public class CanaryRoutingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CanaryRoutingFilter.class);

    private final CanaryProperties canaryProperties;

    public CanaryRoutingFilter(CanaryProperties canaryProperties) {
        this.canaryProperties = canaryProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return chain.filter(exchange);
        }

        CanaryConfig config = canaryProperties.getRoutes().get(route.getId());
        if (config == null || config.getCanaryUri() == null || config.getWeight() <= 0) {
            return chain.filter(exchange);
        }

        int roll = ThreadLocalRandom.current().nextInt(1, 101); // 1..100 inclusive
        if (roll <= config.getWeight()) {
            URI currentUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            if (currentUri != null) {
                URI canaryBase = URI.create(config.getCanaryUri());
                URI newUri = UriComponentsBuilder.fromUri(currentUri)
                        .scheme(canaryBase.getScheme())
                        .host(canaryBase.getHost())
                        .port(canaryBase.getPort())
                        .build(true)
                        .toUri();

                exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, newUri);
                exchange.getAttributes().put("X-Canary", "true");

                log.debug("Canary routing for route '{}': redirecting {} -> {}",
                        route.getId(), currentUri, newUri);
            }
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
