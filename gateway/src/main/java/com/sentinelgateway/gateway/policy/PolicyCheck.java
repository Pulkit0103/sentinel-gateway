package com.sentinelgateway.gateway.policy;

import reactor.core.publisher.Mono;

/**
 * Single-responsibility security policy check.
 *
 * Implementations are Spring components discovered automatically and collected
 * by {@link PolicyEvaluationService}. Add a new enforcement concern by creating
 * a new {@code @Component} that implements this interface — no existing code changes.
 *
 * Contract:
 *   - return {@code Mono.empty()} — check not applicable or policy satisfied
 *   - return {@code Mono.just(violation)} — policy violated; request will be rejected
 */
public interface PolicyCheck {

    Mono<PolicyViolation> evaluate(PolicyEvaluationContext ctx);
}
