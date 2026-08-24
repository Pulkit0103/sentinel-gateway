package com.sentinelgateway.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for tenant-scoped rate limit overrides.
 *
 * <p>When {@code enabled} is {@code true}, a request carrying an {@code X-Tenant-Id}
 * header that matches an entry in {@code tenants} will have its rate limit determined
 * by the tenant-specific {@code requestsPerMinute} value rather than the route-level
 * policy (ANONYMOUS / USER / PREMIUM / DEFAULT).
 *
 * <p>Example YAML configuration:
 * <pre>
 * sentinel:
 *   tenant-rate-limit:
 *     enabled: true
 *     tenants:
 *       acme-corp:
 *         requests-per-minute: 5000
 *       trial-tenant:
 *         requests-per-minute: 50
 * </pre>
 *
 * <p><strong>Phase 14 note:</strong> The filter-level integration (reading
 * {@code TENANT_RATE_LIMIT_RPM} exchange attribute in {@link RateLimitFilter}) is
 * reserved for a future phase. This class serves as the configuration placeholder
 * and is available for injection wherever tenant-specific limits are needed.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.tenant-rate-limit")
public class TenantRateLimitProperties {

    /** Whether tenant-scoped rate limit overrides are active. Defaults to {@code false}. */
    private boolean enabled = false;

    /**
     * Map of tenant ID → tenant-specific rate limit policy.
     * Keys must match the value of the {@code X-Tenant-Id} header exactly.
     */
    private Map<String, TenantPolicy> tenants = new HashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, TenantPolicy> getTenants() { return tenants; }
    public void setTenants(Map<String, TenantPolicy> tenants) { this.tenants = tenants; }

    /**
     * Per-tenant rate limit configuration.
     */
    public static class TenantPolicy {
        private int requestsPerMinute = 1000;

        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
    }
}
