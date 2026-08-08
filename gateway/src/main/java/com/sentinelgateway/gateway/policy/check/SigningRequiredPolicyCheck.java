package com.sentinelgateway.gateway.policy.check;

import com.sentinelgateway.gateway.policy.PolicyCheck;
import com.sentinelgateway.gateway.policy.PolicyEvaluationContext;
import com.sentinelgateway.gateway.policy.PolicyViolation;
import com.sentinelgateway.gateway.signing.HmacAuthentication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Enforces the {@code requestSigningRequired} policy field.
 *
 * Routes with this flag set will only accept requests that carry a valid
 * HMAC-SHA256 signature (i.e., the caller authenticated via
 * {@code HmacVerificationFilter} and the security context holds an
 * {@link HmacAuthentication}).  JWT-only or API-key-only callers are rejected.
 *
 * Requires {@code sentinel.request-signing.enabled=true} at the gateway level;
 * otherwise no HMAC filter runs and this check will always reject.
 */
@Component
public class SigningRequiredPolicyCheck implements PolicyCheck {

    @Override
    public Mono<PolicyViolation> evaluate(PolicyEvaluationContext ctx) {
        if (!ctx.policy().isRequestSigningRequired()) {
            return Mono.empty(); // signing not required for this route
        }

        if (ctx.authentication() instanceof HmacAuthentication) {
            return Mono.empty(); // request was signed and verified
        }

        return Mono.just(new PolicyViolation(HttpStatus.UNAUTHORIZED,
                "Route requires HMAC request signing"));
    }
}
