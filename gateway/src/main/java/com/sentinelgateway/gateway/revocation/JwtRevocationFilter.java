package com.sentinelgateway.gateway.revocation;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.sentinelgateway.gateway.webhook.WebhookEvent;
import com.sentinelgateway.gateway.webhook.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * GlobalFilter that checks incoming JWTs against the Redis revocation blocklist.
 *
 * Runs at {@code HIGHEST_PRECEDENCE + 5} as a GlobalFilter, which means it runs
 * inside Spring Cloud Gateway's filter chain AFTER Spring Security has already
 * authenticated the request and populated the SecurityContext. This ensures
 * the JWT authentication token is available for JTI extraction.
 *
 * Order relative to other GlobalFilters:
 *   JwtRevocationFilter  (HIGHEST_PRECEDENCE + 5)  — blocklist check
 *   JwtHeadersFilter     (HIGHEST_PRECEDENCE + 10) — propagates identity headers
 *
 * Logic:
 * 1. Read the authenticated principal from the ReactiveSecurityContextHolder.
 * 2. If not a JwtAuthenticationToken, skip (API key or anonymous request).
 * 3. Extract the raw JWT token value.
 * 4. Parse the JWT string to extract the JTI claim (without re-verifying signature —
 *    Spring Security has already verified it).
 * 5. If no JTI claim, skip (legitimate tokens may lack JTI).
 * 6. Call {@link TokenRevocationService#isRevoked(String)}.
 * 7. If revoked, return 401 with JSON error body.
 * 8. Otherwise, pass through to the filter chain.
 */
@Component
public class JwtRevocationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtRevocationFilter.class);

    private final TokenRevocationService tokenRevocationService;
    private final WebhookService webhookService;

    public JwtRevocationFilter(TokenRevocationService tokenRevocationService,
                                WebhookService webhookService) {
        this.tokenRevocationService = tokenRevocationService;
        this.webhookService = webhookService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // REACTOR SAFETY: never call chain.filter() inside flatMap branches — Mono<Void>
        // completes empty, which would trigger switchIfEmpty and call chain.filter() twice.
        // Instead, resolve to Mono<ServerWebExchange> (or error), then call chain exactly once.
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
                        return Mono.just(exchange);
                    }

                    String tokenValue = jwtAuth.getToken().getTokenValue();
                    String jti = extractJti(tokenValue);

                    if (jti == null) {
                        return Mono.just(exchange);
                    }

                    return tokenRevocationService.isRevoked(jti)
                            .flatMap(revoked -> {
                                if (revoked) {
                                    log.warn("Rejected revoked token JTI={} for path={}",
                                            jti, exchange.getRequest().getPath());
                                    // Fire-and-forget webhook emission
                                    String requestId = UUID.randomUUID().toString();
                                    String clientIp = resolveClientIp(exchange);
                                    String path = exchange.getRequest().getPath().value();
                                    WebhookEvent event = new WebhookEvent(
                                            "TOKEN_REVOKED", requestId, clientIp, path, null,
                                            Instant.now(), Map.of("jti", jti));
                                    webhookService.emit(event).subscribe(
                                            null,
                                            err -> log.warn("Webhook emit failed: {}", err.getMessage())
                                    );
                                    return Mono.<ServerWebExchange>error(new ResponseStatusException(
                                            HttpStatus.UNAUTHORIZED, "Token has been revoked"));
                                }
                                return Mono.just(exchange);
                            });
                })
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        var addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }

    private String extractJti(String tokenValue) {
        try {
            JWTClaimsSet claims = JWTParser.parse(tokenValue).getJWTClaimsSet();
            return claims.getJWTID();
        } catch (Exception e) {
            log.debug("Could not parse JWT to extract JTI: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
