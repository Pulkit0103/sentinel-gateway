package com.sentinelgateway.gateway.ratelimit;

import com.sentinelgateway.gateway.apikey.ApiKeyAuthentication;
import com.sentinelgateway.gateway.security.AuthenticatedPrincipal;
import com.sentinelgateway.gateway.security.JwtPrincipalExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Applies distributed rate limiting on every routed request.
 *
 * Rate limit key selection:
 *   Authenticated (JWT/API key) → userId / clientId
 *   Anonymous                   → client IP address
 *
 * Policy selection:
 *   Route declares rateLimitPolicy in YAML → that policy
 *   Falls back to USER for authenticated, ANONYMOUS for unauthenticated
 *
 * Response headers on 429:
 *   X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset
 *
 * Runs before proxy dispatch (order HIGHEST_PRECEDENCE + 40).
 */
@Component
@ConditionalOnProperty(name = "sentinel.rate-limit.enabled", havingValue = "true")
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiter rateLimiter;
    private final JwtPrincipalExtractor principalExtractor;

    public RateLimitFilter(RateLimiter rateLimiter, JwtPrincipalExtractor principalExtractor) {
        this.rateLimiter = rateLimiter;
        this.principalExtractor = principalExtractor;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route matchedRoute = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (matchedRoute == null) {
            return chain.filter(exchange);
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> buildRateLimitKey(auth, exchange))
                .map(kp -> kp)
                .defaultIfEmpty(anonymousKey(exchange))
                .flatMap(keyAndPolicy -> rateLimiter.checkAndIncrement(
                        keyAndPolicy.key(), keyAndPolicy.policy()))
                .flatMap(result -> applyResult(result, exchange, chain));
    }

    private Mono<KeyAndPolicy> buildRateLimitKey(Authentication auth, ServerWebExchange exchange) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            AuthenticatedPrincipal p = principalExtractor.extract(jwtAuth.getToken());
            return Mono.just(new KeyAndPolicy(p.userId(), RateLimitPolicy.USER));
        }
        if (auth instanceof ApiKeyAuthentication apiKeyAuth) {
            AuthenticatedPrincipal p = apiKeyAuth.getPrincipal();
            return Mono.just(new KeyAndPolicy(p.clientId(), RateLimitPolicy.USER));
        }
        return Mono.empty();
    }

    private KeyAndPolicy anonymousKey(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String ip = remote != null ? remote.getAddress().getHostAddress() : "unknown";
        return new KeyAndPolicy(ip, RateLimitPolicy.ANONYMOUS);
    }

    private Mono<Void> applyResult(RateLimitResult result, ServerWebExchange exchange,
                                    GatewayFilterChain chain) {
        var response = exchange.getResponse();
        response.getHeaders().add("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.getHeaders().add("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.getHeaders().add("X-RateLimit-Reset", String.valueOf(result.resetAfterSecs()));

        if (!result.allowed()) {
            log.warn("Rate limit exceeded for request to '{}'",
                    exchange.getRequest().getPath());
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return response.setComplete();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 40; // after TenantIsolationFilter (+30)
    }

    private record KeyAndPolicy(String key, RateLimitPolicy policy) {}
}
