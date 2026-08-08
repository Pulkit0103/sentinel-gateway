package com.sentinelgateway.gateway.policy;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Route-level security policy stored in the database.
 *
 * One policy per route (enforced by unique constraint on route_id).
 * Created at startup from {@code sentinel.policy.policies[*]} config;
 * updatable at runtime via the Admin API (Phase 17).
 *
 * Fields:
 *   requireMfa              — caller's JWT must carry amr=[mfa]
 *   requestSigningRequired  — caller must present a valid HMAC signature
 *   requiredScopes          — comma-separated additional scope requirements
 *   allowedMethods          — comma-separated; empty means all methods allowed
 *   rateLimitPolicy         — policy tier name (ANONYMOUS/USER/PREMIUM/DEFAULT)
 */
@Table("security_policies")
public class SecurityPolicy {

    @Id
    private Long id;

    private String policyId;
    private String routeId;
    private boolean requireMfa;
    private boolean requestSigningRequired;
    private String requiredScopes;   // comma-separated; null or blank = no extra scopes
    private String allowedMethods;   // comma-separated; null or blank = all methods
    private String rateLimitPolicy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SecurityPolicy() {}

    public SecurityPolicy(String policyId, String routeId,
                          boolean requireMfa, boolean requestSigningRequired,
                          List<String> requiredScopes, List<String> allowedMethods,
                          String rateLimitPolicy) {
        this.policyId = policyId;
        this.routeId = routeId;
        this.requireMfa = requireMfa;
        this.requestSigningRequired = requestSigningRequired;
        this.requiredScopes = joinOrNull(requiredScopes);
        this.allowedMethods = joinOrNull(allowedMethods);
        this.rateLimitPolicy = rateLimitPolicy != null ? rateLimitPolicy : "DEFAULT";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    private static String joinOrNull(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return String.join(",", list);
    }

    private static List<String> splitOrEmpty(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public List<String> requiredScopesList()  { return splitOrEmpty(requiredScopes); }
    public List<String> allowedMethodsList()   { return splitOrEmpty(allowedMethods); }

    // ── getters / setters (required by R2DBC) ────────────────────────────────

    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }

    public String getPolicyId()                { return policyId; }
    public void setPolicyId(String v)          { this.policyId = v; }

    public String getRouteId()                 { return routeId; }
    public void setRouteId(String v)           { this.routeId = v; }

    public boolean isRequireMfa()              { return requireMfa; }
    public void setRequireMfa(boolean v)       { this.requireMfa = v; }

    public boolean isRequestSigningRequired()  { return requestSigningRequired; }
    public void setRequestSigningRequired(boolean v) { this.requestSigningRequired = v; }

    public String getRequiredScopes()          { return requiredScopes; }
    public void setRequiredScopes(String v)    { this.requiredScopes = v; }

    public String getAllowedMethods()          { return allowedMethods; }
    public void setAllowedMethods(String v)   { this.allowedMethods = v; }

    public String getRateLimitPolicy()         { return rateLimitPolicy; }
    public void setRateLimitPolicy(String v)   { this.rateLimitPolicy = v; }

    public LocalDateTime getCreatedAt()        { return createdAt; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }

    public LocalDateTime getUpdatedAt()        { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)  { this.updatedAt = v; }

    @Override
    public String toString() {
        return "SecurityPolicy{policyId='" + policyId + "', routeId='" + routeId
                + "', requireMfa=" + requireMfa
                + ", requestSigningRequired=" + requestSigningRequired + "}";
    }
}
