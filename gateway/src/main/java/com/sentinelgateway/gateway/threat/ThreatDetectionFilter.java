package com.sentinelgateway.gateway.threat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * WAF-style threat detection filter that runs before all authentication.
 *
 * On each request it:
 *   1. Checks the client IP against the static blocked-IP list → 403 immediately
 *   2. Runs all registered {@link ThreatDetector}s (pattern-based, synchronous)
 *   3. Sums signal scores and maps to an action via {@link ThreatRiskScorer}
 *   4. ALLOW → passes through silently
 *      LOG   → passes through with an INFO log
 *      BLOCK → rejects with 400 Bad Request
 *      BLOCK_AND_ALERT → rejects with 400 and logs at ERROR severity
 *
 * Runs at {@code HIGHEST_PRECEDENCE + 2} — before API key auth (+5),
 * HMAC verification (+8), and Spring Security (0).
 *
 * /actuator/** is excluded — health probes should never be blocked by the WAF.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@ConditionalOnProperty(name = "sentinel.threat-detection.enabled", havingValue = "true")
public class ThreatDetectionFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ThreatDetectionFilter.class);

    private final List<ThreatDetector> detectors;
    private final ThreatRiskScorer scorer;
    private final ThreatDetectionProperties properties;

    public ThreatDetectionFilter(List<ThreatDetector> detectors,
                                 ThreatRiskScorer scorer,
                                 ThreatDetectionProperties properties) {
        this.detectors = detectors;
        this.scorer = scorer;
        this.properties = properties;
        log.debug("Threat detection enabled with {} detector(s)", detectors.size());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();

        // Exempt actuator from WAF scanning (health probes, metrics scrapers)
        if (req.getPath().value().startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        // 1 — blocked IP check
        String clientIp = extractClientIp(req);
        if (properties.getBlockedIps().contains(clientIp)) {
            log.warn("Blocked IP [{}] denied access to {}", clientIp, req.getPath());
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // 2 — pattern detection (synchronous, CPU-only)
        List<ThreatSignal> signals = detectors.stream()
                .flatMap(d -> d.detect(exchange).stream())
                .toList();

        int totalScore = scorer.totalScore(signals);
        ThreatAction action = scorer.determineAction(totalScore);

        return switch (action) {
            case ALLOW -> chain.filter(exchange);

            case LOG -> {
                log.info("Suspicious request (score={}) from [{}]: {} — {}",
                        totalScore, clientIp, req.getURI(), signals);
                yield chain.filter(exchange);
            }

            case BLOCK -> {
                log.warn("Threat blocked (score={}) from [{}]: {} {}",
                        totalScore, clientIp, req.getMethod(), req.getURI());
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                yield exchange.getResponse().setComplete();
            }

            case BLOCK_AND_ALERT -> {
                log.error("HIGH RISK THREAT BLOCKED (score={}) from [{}]: {} {} — signals: {}",
                        totalScore, clientIp, req.getMethod(), req.getURI(), signals);
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                yield exchange.getResponse().setComplete();
            }
        };
    }

    private String extractClientIp(ServerHttpRequest req) {
        // X-Forwarded-For carries the originating client IP when behind a proxy/LB.
        // First value in the comma-separated list is the original client.
        String forwarded = req.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress addr = req.getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }
}
