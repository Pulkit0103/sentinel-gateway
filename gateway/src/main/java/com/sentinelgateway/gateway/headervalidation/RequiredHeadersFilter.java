package com.sentinelgateway.gateway.headervalidation;

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
import java.util.List;
import java.util.stream.Collectors;

/**
 * GlobalFilter that validates required headers are present for configured routes.
 *
 * Runs at {@code Ordered.HIGHEST_PRECEDENCE + 5} — after route matching (so
 * {@code GATEWAY_ROUTE_ATTR} is populated) but before upstream proxying.
 *
 * If a required header is missing, returns 400 Bad Request with a JSON body:
 * {@code {"status":400,"error":"Missing required header","missing":["X-Idempotency-Key"]}}
 *
 * The filter is a no-op when {@code sentinel.required-headers.enabled=false} or
 * when the matched route has no configured required headers.
 */
@Component
public class RequiredHeadersFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequiredHeadersFilter.class);

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

    private final RequiredHeadersProperties properties;

    public RequiredHeadersFilter(RequiredHeadersProperties properties) {
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
        if (route == null) {
            return chain.filter(exchange);
        }

        List<String> required = properties.requiredHeadersForRoute(route.getId());
        if (required.isEmpty()) {
            return chain.filter(exchange);
        }

        List<String> missing = required.stream()
                .filter(h -> !exchange.getRequest().getHeaders().containsKey(h))
                .collect(Collectors.toList());

        if (missing.isEmpty()) {
            return chain.filter(exchange);
        }

        log.debug("Route {} missing required headers: {}", route.getId(), missing);
        return sendBadRequest(exchange, missing);
    }

    private Mono<Void> sendBadRequest(ServerWebExchange exchange, List<String> missing) {
        String missingJson = missing.stream()
                .map(h -> "\"" + h + "\"")
                .collect(Collectors.joining(","));
        String body = String.format(
                "{\"status\":400,\"error\":\"Missing required header\",\"missing\":[%s]}", missingJson);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(bytes.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
