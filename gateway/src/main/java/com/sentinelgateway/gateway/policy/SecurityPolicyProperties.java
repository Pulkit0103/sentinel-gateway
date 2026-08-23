package com.sentinelgateway.gateway.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads security policies from {@code sentinel.policy.policies[*]} in application.yml.
 *
 * Policies are loaded into the database at startup by {@link SecurityPolicyLoader}.
 * The DB is the runtime source of truth; YAML is the initial seed and can be
 * updated via the Admin API in Phase 17.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.policy")
public class SecurityPolicyProperties {

    private List<PolicyEntry> policies = new ArrayList<>();

    public List<PolicyEntry> getPolicies()             { return policies; }
    public void setPolicies(List<PolicyEntry> policies) {
        this.policies = policies != null ? policies : new ArrayList<>();
    }

    public static class PolicyEntry {
        private String policyId;
        private String routeId;
        private boolean requireMfa = false;
        private boolean requestSigningRequired = false;
        private List<String> requiredScopes = new ArrayList<>();
        private List<String> allowedMethods = new ArrayList<>();
        private String rateLimitPolicy = "DEFAULT";

        public SecurityPolicy toSecurityPolicy() {
            return new SecurityPolicy(
                    policyId, routeId,
                    requireMfa, requestSigningRequired,
                    requiredScopes, allowedMethods,
                    rateLimitPolicy);
        }

        // ── getters / setters ─────────────────────────────────────────────

        public String getPolicyId()                      { return policyId; }
        public void setPolicyId(String v)                { this.policyId = v; }

        public String getRouteId()                       { return routeId; }
        public void setRouteId(String v)                 { this.routeId = v; }

        public boolean isRequireMfa()                    { return requireMfa; }
        public void setRequireMfa(boolean v)             { this.requireMfa = v; }

        public boolean isRequestSigningRequired()        { return requestSigningRequired; }
        public void setRequestSigningRequired(boolean v) { this.requestSigningRequired = v; }

        public List<String> getRequiredScopes()          { return requiredScopes; }
        public void setRequiredScopes(List<String> v)    { this.requiredScopes = v != null ? v : new ArrayList<>(); }

        public List<String> getAllowedMethods()           { return allowedMethods; }
        public void setAllowedMethods(List<String> v)    { this.allowedMethods = v != null ? v : new ArrayList<>(); }

        public String getRateLimitPolicy()               { return rateLimitPolicy; }
        public void setRateLimitPolicy(String v)         { this.rateLimitPolicy = v; }
    }
}
