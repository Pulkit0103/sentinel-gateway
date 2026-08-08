package com.sentinelgateway.gateway.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Applies the {@link SecurityPolicy} for the matched route.
 *
 * Runs after route authorization (+20) and before tenant isolation (+30).
 * For each routed request it:
 *   1. Looks up the policy for the matched route (pass-through if no policy exists)
 *   2. Reads the current authentication from the Reactor security context
 *   3. Delegates to {@link PolicyEvaluationService} which iterates all {@link PolicyCheck}s
 *   4. Rejects with the first violation's HTTP status, or passes through if all checks pass
 *
 * Reactor safety: uses {@code defaultIfEmpty} + {@code flatMap} — never
 * {@code switchIfEmpty} after a {@code Mono<Void>} pipeline.
 */
@Component
public class PolicyEnforcementFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PolicyEnforcementFilter.class);

    private final SecurityPolicyRepository repository;
    private final PolicyEvaluationService evaluationService;

    public PolicyEnforcementFilter(SecurityPolicyRepository repository,
                                   PolicyEvaluationService evaluationService) {
        this.repository = repository;
        this.evaluationService = evaluationService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route matchedRoute = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (matchedRoute == null) {
            return chain.filter(exchange);
        }

        return repository.findByRouteId(matchedRoute.getId())
                .map(p -> (Object) p)
                .defaultIfEmpty(Boolean.FALSE)
                .flatMap(obj -> {
                    if (!(obj instanceof SecurityPolicy policy)) {
                        return chain.filter(exchange); // no policy → pass through
                    }

                    return ReactiveSecurityContextHolder.getContext()
                            .map(ctx -> (Authentication) ctx.getAuthentication())
                            .map(auth -> (Object) auth)
                            .defaultIfEmpty(Boolean.FALSE)
                            .flatMap(authObj -> {
                                Authentication auth = authObj instanceof Authentication a ? a : null;
                                PolicyEvaluationContext ctx =
                                        new PolicyEvaluationContext(exchange, policy, auth);

                                return evaluationService.evaluate(ctx)
                                        .map(v -> (Object) v)
                                        .defaultIfEmpty(Boolean.FALSE)
                                        .flatMap(result -> {
                                            if (result instanceof PolicyViolation violation) {
                                                log.warn("Policy '{}' denied request to '{}': {}",
                                                        policy.getPolicyId(), matchedRoute.getId(),
                                                        violation.reason());
                                                exchange.getResponse().setStatusCode(violation.status());
                                                return exchange.getResponse().setComplete();
                                            }
                                            return chain.filter(exchange);
                                        });
                            });
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 25; // after RouteAuthorization (+20), before TenantIsolation (+30)
    }
}
