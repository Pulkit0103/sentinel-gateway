package com.sentinelgateway.gateway.transform;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Strips client-supplied identity headers before JwtHeadersFilter runs.
 *
 * Prevents header injection: if a client sends X-User-Id: attacker, this filter
 * removes it so only the verified value added by JwtHeadersFilter reaches upstream.
 *
 * Also strips any header whose name starts with X-Internal-.
 *
 * Order HIGHEST_PRECEDENCE+2 ensures this runs before JwtHeadersFilter (HIGHEST_PRECEDENCE+10).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestSanitizationFilter implements WebFilter {

    private final TransformProperties transformProperties;

    public RequestSanitizationFilter(TransformProperties transformProperties) {
        this.transformProperties = transformProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        List<String> headersToStrip = transformProperties.getSanitizeRequestHeaders();

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    headersToStrip.forEach(headers::remove);
                    headers.keySet().stream()
                            .filter(name -> name.toLowerCase().startsWith("x-internal-"))
                            .toList()
                            .forEach(headers::remove);
                })
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }
}
