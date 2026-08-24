package com.sentinelgateway.gateway.sanitizer;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * WebFilter that rejects requests with malformed or oversized header values (Phase 32).
 *
 * Runs at HIGHEST_PRECEDENCE + 3, early in the pipeline before authentication.
 * Checks every header value for:
 *   - Exceeding {@code maxHeaderValueLength} bytes
 *   - Containing null bytes (\0) when {@code blockNullBytes=true}
 *
 * Returns 400 Bad Request with a JSON body describing which header violated the policy.
 */
@Component
public class RequestSanitizerFilter implements WebFilter, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 3;

    private final RequestSanitizerProperties properties;

    public RequestSanitizerFilter(RequestSanitizerProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        for (Map.Entry<String, List<String>> entry : exchange.getRequest().getHeaders().entrySet()) {
            String headerName = entry.getKey();
            if (properties.isSkipped(headerName)) continue;
            for (String value : entry.getValue()) {
                if (value.length() > properties.getMaxHeaderValueLength()) {
                    return sendBadRequest(exchange,
                            "Header value too long: " + headerName +
                            " (max " + properties.getMaxHeaderValueLength() + " chars)");
                }
                if (properties.isBlockNullBytes() && value.indexOf('\0') >= 0) {
                    return sendBadRequest(exchange, "Null byte in header: " + headerName);
                }
            }
        }

        return chain.filter(exchange);
    }

    private Mono<Void> sendBadRequest(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"status\":400,\"error\":\"Bad Request\",\"detail\":\"%s\"}", reason);
        DataBuffer buf = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buf));
    }
}
