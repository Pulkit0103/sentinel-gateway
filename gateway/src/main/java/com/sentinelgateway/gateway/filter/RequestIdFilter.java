package com.sentinelgateway.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Assigns a unique request ID to every inbound request.
 * Propagates it downstream as X-Request-ID and echoes it in the response.
 *
 * Implemented as a WebFilter (not a GlobalFilter) so it applies to ALL
 * requests — including actuator endpoints and gateway-routed requests.
 *
 * If the client already provides X-Request-ID it is preserved, which supports
 * end-to-end correlation when the caller is a trusted upstream service.
 */
@Component
public class RequestIdFilter implements WebFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders()
                .getFirst(REQUEST_ID_HEADER);

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        final String resolvedRequestId = requestId;

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, resolvedRequestId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Set the response header before the filter chain runs.
        // Response headers are mutable until the response is committed.
        mutatedExchange.getResponse().getHeaders()
                .set(REQUEST_ID_HEADER, resolvedRequestId);

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
