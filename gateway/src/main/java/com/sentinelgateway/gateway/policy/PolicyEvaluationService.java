package com.sentinelgateway.gateway.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Runs all registered {@link PolicyCheck} implementations against a request.
 *
 * Checks run sequentially (not in parallel) so the first violation short-circuits
 * evaluation — no need to examine the rest once a deny decision is made.
 *
 * New security concerns are added by creating additional {@code @Component}
 * beans that implement {@link PolicyCheck}.  This class never changes.
 */
@Service
public class PolicyEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(PolicyEvaluationService.class);

    private final List<PolicyCheck> checks;

    public PolicyEvaluationService(List<PolicyCheck> checks) {
        this.checks = checks;
        log.debug("Policy engine registered {} check(s): {}", checks.size(),
                checks.stream().map(c -> c.getClass().getSimpleName()).toList());
    }

    /**
     * Evaluates the policy against the request context.
     *
     * @return Mono.empty() when all checks pass; Mono.just(violation) on first failure
     */
    public Mono<PolicyViolation> evaluate(PolicyEvaluationContext ctx) {
        return Flux.fromIterable(checks)
                .concatMap(check -> check.evaluate(ctx))
                .next(); // stop at first violation (next() completes after first element)
    }
}
