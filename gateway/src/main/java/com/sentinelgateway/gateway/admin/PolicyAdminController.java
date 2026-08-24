package com.sentinelgateway.gateway.admin;

import com.sentinelgateway.gateway.policy.SecurityPolicy;
import com.sentinelgateway.gateway.policy.SecurityPolicyRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Admin REST API for security policy lifecycle management.
 *
 * Policies are persisted in the database and take effect for new requests
 * immediately after creation or update (PolicyEnforcementFilter reads from DB).
 *
 * Secured by ROLE_ADMIN (configured in SecurityConfig).
 */
@RestController
@RequestMapping("/admin/policies")
public class PolicyAdminController {

    private final SecurityPolicyRepository repository;

    public PolicyAdminController(SecurityPolicyRepository repository) {
        this.repository = repository;
    }

    /** List all security policies. */
    @GetMapping
    public Flux<PolicyResponse> listPolicies() {
        return repository.findAll().map(PolicyResponse::from);
    }

    /** Get a single policy by policyId. */
    @GetMapping("/{policyId}")
    public Mono<PolicyResponse> getPolicy(@PathVariable String policyId) {
        return repository.findByPolicyId(policyId)
                .map(PolicyResponse::from)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Policy not found: " + policyId)));
    }

    /** Get the policy for a specific route. */
    @GetMapping("/route/{routeId}")
    public Mono<PolicyResponse> getPolicyByRoute(@PathVariable String routeId) {
        return repository.findByRouteId(routeId)
                .map(PolicyResponse::from)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No policy for route: " + routeId)));
    }

    /** Create a new security policy. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PolicyResponse> createPolicy(@Valid @RequestBody PolicyRequest request) {
        return repository.findByRouteId(request.routeId())
                .flatMap(existing -> Mono.<PolicyResponse>error(new ResponseStatusException(
                        HttpStatus.CONFLICT, "Policy already exists for route: " + request.routeId())))
                .switchIfEmpty(Mono.defer(() -> {
                    var policy = new SecurityPolicy(
                            UUID.randomUUID().toString(),
                            request.routeId(),
                            request.requireMfa() != null && request.requireMfa(),
                            request.requestSigningRequired() != null && request.requestSigningRequired(),
                            request.requiredScopes() != null ? request.requiredScopes() : List.of(),
                            request.allowedMethods() != null ? request.allowedMethods() : List.of(),
                            request.rateLimitPolicy() != null ? request.rateLimitPolicy() : "DEFAULT"
                    );
                    return repository.save(policy).map(PolicyResponse::from);
                }));
    }

    /** Update an existing policy. */
    @PutMapping("/{policyId}")
    public Mono<PolicyResponse> updatePolicy(@PathVariable String policyId,
                                             @Valid @RequestBody PolicyRequest request) {
        return repository.findByPolicyId(policyId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Policy not found: " + policyId)))
                .flatMap(policy -> {
                    policy.setRequireMfa(request.requireMfa() != null && request.requireMfa());
                    policy.setRequestSigningRequired(request.requestSigningRequired() != null
                            && request.requestSigningRequired());
                    policy.setRequiredScopes(request.requiredScopes() != null
                            ? String.join(",", request.requiredScopes()) : null);
                    policy.setAllowedMethods(request.allowedMethods() != null
                            ? String.join(",", request.allowedMethods()) : null);
                    policy.setRateLimitPolicy(request.rateLimitPolicy() != null
                            ? request.rateLimitPolicy() : "DEFAULT");
                    policy.setUpdatedAt(LocalDateTime.now());
                    return repository.save(policy);
                })
                .map(PolicyResponse::from);
    }

    /** Delete a security policy. The route will revert to default (no policy enforcement). */
    @DeleteMapping("/{policyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deletePolicy(@PathVariable String policyId) {
        return repository.findByPolicyId(policyId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Policy not found: " + policyId)))
                .flatMap(repository::delete);
    }

    // ── Request / Response Records ────────────────────────────────────────────

    record PolicyRequest(
            @NotBlank String routeId,
            Boolean requireMfa,
            Boolean requestSigningRequired,
            List<String> requiredScopes,
            List<@Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") String> allowedMethods,
            @Pattern(regexp = "ANONYMOUS|USER|PREMIUM|DEFAULT") String rateLimitPolicy
    ) {}

    record PolicyResponse(
            Long id, String policyId, String routeId,
            boolean requireMfa, boolean requestSigningRequired,
            List<String> requiredScopes, List<String> allowedMethods,
            String rateLimitPolicy, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        static PolicyResponse from(SecurityPolicy p) {
            return new PolicyResponse(
                    p.getId(), p.getPolicyId(), p.getRouteId(),
                    p.isRequireMfa(), p.isRequestSigningRequired(),
                    p.requiredScopesList(), p.allowedMethodsList(),
                    p.getRateLimitPolicy(), p.getCreatedAt(), p.getUpdatedAt()
            );
        }
    }
}
