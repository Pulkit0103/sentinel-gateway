package com.sentinelgateway.gateway.admin;

import com.sentinelgateway.gateway.ratelimit.RateLimitProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin REST API for inspecting active rate limit policies.
 *
 * Returns the current policy configuration loaded from {@code sentinel.rate-limit.policies}
 * in application.yml (or any override source). This allows operators to verify
 * which policies are active without restarting or inspecting config files directly.
 *
 * Secured by ROLE_ADMIN (configured in SecurityConfig).
 */
@RestController
@RequestMapping("/admin/rate-limit")
public class RateLimitPolicyController {

    private final RateLimitProperties rateLimitProperties;

    public RateLimitPolicyController(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * GET /admin/rate-limit/policies
     *
     * Returns the map of policy name → policy config. Example response:
     * <pre>
     * {
     *   "ANONYMOUS": {"requestsPerMinute": 100},
     *   "USER":      {"requestsPerMinute": 1000},
     *   "PREMIUM":   {"requestsPerMinute": 10000},
     *   "DEFAULT":   {"requestsPerMinute": 1000}
     * }
     * </pre>
     */
    @GetMapping("/policies")
    public Mono<Map<String, RateLimitProperties.PolicyConfig>> getPolicies() {
        return Mono.just(rateLimitProperties.getPolicies());
    }
}
