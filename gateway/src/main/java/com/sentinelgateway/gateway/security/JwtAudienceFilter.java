package com.sentinelgateway.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * WebFilter that validates the {@code aud} (audience) claim in JWT bearer tokens.
 *
 * When {@link JwtAudienceProperties#isAudienceValidationEnabled()} is true, every
 * JWT-authenticated request must present a token whose {@code aud} claim contains
 * at least one of the configured required-audience values.
 *
 * Non-JWT authentication (API keys, anonymous paths) passes through unchanged.
 *
 * Runs after Spring Security authentication (order = 0) so the security context
 * is already populated when this filter runs.
 */
@Component
public class JwtAudienceFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAudienceFilter.class);

    public static final int ORDER = 0;

    private final JwtAudienceProperties properties;

    public JwtAudienceFilter(JwtAudienceProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isAudienceValidationEnabled()) {
            return chain.filter(exchange);
        }

        // Map security context to a boolean: true = valid (proceed), false = reject.
        // defaultIfEmpty(true) handles unauthenticated paths — let Spring Security handle those.
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> {
                    if (!(ctx.getAuthentication() instanceof JwtAuthenticationToken jwtAuth)) {
                        return true; // non-JWT auth; not our concern
                    }
                    List<String> tokenAudiences = jwtAuth.getToken().getAudience();
                    if (tokenAudiences == null || tokenAudiences.isEmpty()) {
                        log.warn("JWT missing audience claim; required one of: {}",
                                properties.getRequiredAudiences());
                        return false;
                    }
                    boolean valid = tokenAudiences.stream()
                            .anyMatch(aud -> properties.getRequiredAudiences().contains(aud));
                    if (!valid) {
                        log.warn("JWT audience mismatch — token aud={}, required one of={}",
                                tokenAudiences, properties.getRequiredAudiences());
                    }
                    return valid;
                })
                .defaultIfEmpty(true)
                .flatMap(valid -> valid ? chain.filter(exchange) : sendUnauthorized(exchange));
    }

    private Mono<Void> sendUnauthorized(ServerWebExchange exchange) {
        String body = "{\"status\":401,\"error\":\"Invalid JWT audience\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(bytes.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
