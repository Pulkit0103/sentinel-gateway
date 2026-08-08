package com.sentinelgateway.gateway.quota;

import com.sentinelgateway.gateway.apikey.ApiKeyAuthentication;
import com.sentinelgateway.gateway.security.AuthenticatedPrincipal;
import com.sentinelgateway.gateway.security.JwtPrincipalExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enforces per-tenant daily quotas on routed requests.
 *
 * Only runs when sentinel.quota.enabled=true and a tenantId is present
 * in the authenticated principal. Anonymous requests are not quota-checked
 * (they are rate-limited instead).
 *
 * 429 with X-Quota-* headers when exceeded.
 * Runs after RateLimitFilter (order HIGHEST_PRECEDENCE + 50).
 */
@Component
@ConditionalOnProperty(name = "sentinel.quota.enabled", havingValue = "true")
public class QuotaEnforcementFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(QuotaEnforcementFilter.class);

    private final QuotaChecker quotaChecker;
    private final JwtPrincipalExtractor principalExtractor;

    public QuotaEnforcementFilter(QuotaChecker quotaChecker, JwtPrincipalExtractor principalExtractor) {
        this.quotaChecker = quotaChecker;
        this.principalExtractor = principalExtractor;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> extractPrincipal(auth))
                .filter(p -> p.tenantId() != null && !p.tenantId().isBlank())
                .flatMap(p -> quotaChecker.checkAndIncrement(p.tenantId(), QuotaPeriod.DAILY))
                .map(r -> (Object) r)
                .defaultIfEmpty(Boolean.TRUE) // no tenant or no auth → pass through
                .flatMap(obj -> {
                    if (obj instanceof QuotaResult result) {
                        var response = exchange.getResponse();
                        response.getHeaders().add("X-Quota-Limit", String.valueOf(result.limit()));
                        response.getHeaders().add("X-Quota-Used", String.valueOf(result.used()));
                        response.getHeaders().add("X-Quota-Reset", String.valueOf(result.resetEpochSecs()));

                        if (!result.allowed()) {
                            log.warn("Daily quota exceeded for tenant");
                            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                            return response.setComplete();
                        }
                    }
                    return chain.filter(exchange);
                });
    }

    private Mono<AuthenticatedPrincipal> extractPrincipal(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Mono.just(principalExtractor.extract(jwtAuth.getToken()));
        }
        if (auth instanceof ApiKeyAuthentication apiKeyAuth) {
            return Mono.just(apiKeyAuth.getPrincipal());
        }
        return Mono.empty();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 50; // after RateLimitFilter (+40)
    }
}
