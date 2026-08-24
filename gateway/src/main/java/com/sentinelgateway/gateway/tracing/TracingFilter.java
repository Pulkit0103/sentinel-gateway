package com.sentinelgateway.gateway.tracing;

import com.sentinelgateway.gateway.filter.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Propagates W3C Trace Context headers (traceparent, tracestate, baggage) for
 * distributed request correlation across upstream services.
 *
 * Runs at {@code HIGHEST_PRECEDENCE + 3} — after RequestSanitizationFilter (+2)
 * which has already stripped client-supplied identity headers, and before
 * JwtRevocationFilter (+5) which is a GlobalFilter.
 *
 * Logic:
 * 1. If tracing is disabled, pass through unchanged.
 * 2. Read the X-Request-ID set by RequestIdFilter (or generate a fallback).
 * 3. If the client supplied a valid W3C traceparent and propagateExisting=true,
 *    preserve the trace-id and generate a new parent-id for this hop.
 * 4. Otherwise generate a fully new traceparent (new trace-id + parent-id).
 * 5. Set:
 *    - traceparent: 00-{traceId}-{parentId}-01
 *    - tracestate:  sentinel=1
 *    - baggage:     request-id={X-Request-ID}
 * 6. Strip client-supplied tracestate and baggage to prevent injection.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class TracingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(TracingFilter.class);

    static final String TRACEPARENT_HEADER = "traceparent";
    static final String TRACESTATE_HEADER  = "tracestate";
    static final String BAGGAGE_HEADER     = "baggage";

    private final TracingProperties properties;

    public TracingFilter(TracingProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();

        // 1 — obtain the request-id already placed by RequestIdFilter (order=HIGHEST_PRECEDENCE)
        String requestId = request.getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        // 2 — decide trace-id: reuse from client or generate fresh
        String clientTraceparent = request.getHeaders().getFirst(TRACEPARENT_HEADER);
        String traceId;
        if (properties.isPropagateExisting() && isValidTraceparent(clientTraceparent)) {
            // Extract trace-id from existing valid traceparent (field index 1)
            traceId = clientTraceparent.split("-")[1];
            log.debug("Continuing existing trace {}", traceId);
        } else {
            traceId = newTraceId();
            log.debug("Starting new trace {}", traceId);
        }

        // 3 — always generate a fresh parent-id for this gateway hop
        String parentId  = newParentId();
        String traceparent = buildTraceparent(traceId, parentId);

        // 4 — build baggage carrying the request-id for cross-service correlation
        String baggage = "request-id=" + requestId;

        final String resolvedRequestId = requestId;

        // 5 — mutate the request: set tracing headers, strip client tracestate/baggage
        ServerHttpRequest mutated = request.mutate()
                .headers(headers -> {
                    // Strip client-supplied values to prevent injection
                    headers.remove(TRACESTATE_HEADER);
                    headers.remove(BAGGAGE_HEADER);
                    // Set our controlled values
                    headers.set(TRACEPARENT_HEADER, traceparent);
                    headers.set(TRACESTATE_HEADER,  "sentinel=1");
                    headers.set(BAGGAGE_HEADER,      baggage);
                })
                .build();

        log.debug("Tracing headers set: traceparent={} baggage={}",
                traceparent, baggage);

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    // ── W3C Trace Context validation ──────────────────────────────────────────

    private boolean isValidTraceparent(String tp) {
        if (tp == null) return false;
        String[] parts = tp.split("-");
        return parts.length == 4
                && parts[0].equals("00")
                && parts[1].length() == 32
                && parts[2].length() == 16
                && parts[3].length() == 2
                && parts[1].matches("[0-9a-f]+")
                && parts[2].matches("[0-9a-f]+");
    }

    // ── ID generation ─────────────────────────────────────────────────────────

    private String newTraceId() {
        UUID uuid = UUID.randomUUID();
        return String.format("%016x%016x",
                uuid.getMostSignificantBits(),
                uuid.getLeastSignificantBits());
    }

    private String newParentId() {
        return String.format("%016x", UUID.randomUUID().getMostSignificantBits());
    }

    private String buildTraceparent(String traceId, String parentId) {
        return "00-" + traceId + "-" + parentId + "-01";
    }
}
