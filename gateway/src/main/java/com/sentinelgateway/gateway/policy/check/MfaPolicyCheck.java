package com.sentinelgateway.gateway.policy.check;

import com.sentinelgateway.gateway.policy.PolicyCheck;
import com.sentinelgateway.gateway.policy.PolicyEvaluationContext;
import com.sentinelgateway.gateway.policy.PolicyViolation;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Enforces the {@code requireMfa} policy field.
 *
 * When the policy requires MFA, the caller's JWT must carry {@code amr=["mfa"]}
 * (Authentication Methods Reference — RFC 8176).  Machine-to-machine callers
 * authenticated via HMAC or API key bypass this check because they are
 * service identities, not end-users.
 */
@Component
public class MfaPolicyCheck implements PolicyCheck {

    @Override
    public Mono<PolicyViolation> evaluate(PolicyEvaluationContext ctx) {
        if (!ctx.policy().isRequireMfa()) {
            return Mono.empty(); // MFA not required for this route
        }

        if (!(ctx.authentication() instanceof JwtAuthenticationToken jwtAuth)) {
            // Non-JWT callers (HMAC, API key) are service identities — MFA doesn't apply
            return Mono.empty();
        }

        Object amrClaim = jwtAuth.getToken().getClaim("amr");
        if (amrClaim instanceof List<?> amrList && amrList.contains("mfa")) {
            return Mono.empty(); // MFA confirmed
        }

        return Mono.just(new PolicyViolation(HttpStatus.FORBIDDEN,
                "Route requires multi-factor authentication (amr=mfa)"));
    }
}
