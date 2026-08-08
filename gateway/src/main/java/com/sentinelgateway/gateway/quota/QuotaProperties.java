package com.sentinelgateway.gateway.quota;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for per-tenant quota limits.
 *
 * Quota config in application.yml:
 *   sentinel.quota:
 *     enabled: true
 *     default-daily-limit: 100000
 *     default-monthly-limit: 2000000
 *     tenants:
 *       tenant-premium:
 *         daily-limit: 1000000
 *         monthly-limit: 20000000
 */
@Component
@ConfigurationProperties(prefix = "sentinel.quota")
public class QuotaProperties {

    private boolean enabled = false;
    private long defaultDailyLimit = 100_000;
    private long defaultMonthlyLimit = 2_000_000;
    private Map<String, TenantQuota> tenants = new HashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getDefaultDailyLimit() { return defaultDailyLimit; }
    public void setDefaultDailyLimit(long defaultDailyLimit) { this.defaultDailyLimit = defaultDailyLimit; }
    public long getDefaultMonthlyLimit() { return defaultMonthlyLimit; }
    public void setDefaultMonthlyLimit(long defaultMonthlyLimit) { this.defaultMonthlyLimit = defaultMonthlyLimit; }
    public Map<String, TenantQuota> getTenants() { return tenants; }
    public void setTenants(Map<String, TenantQuota> tenants) { this.tenants = tenants; }

    public long dailyLimitFor(String tenantId) {
        TenantQuota tq = tenants.get(tenantId);
        return tq != null && tq.getDailyLimit() > 0 ? tq.getDailyLimit() : defaultDailyLimit;
    }

    public long monthlyLimitFor(String tenantId) {
        TenantQuota tq = tenants.get(tenantId);
        return tq != null && tq.getMonthlyLimit() > 0 ? tq.getMonthlyLimit() : defaultMonthlyLimit;
    }

    public static class TenantQuota {
        private long dailyLimit;
        private long monthlyLimit;
        public long getDailyLimit() { return dailyLimit; }
        public void setDailyLimit(long dailyLimit) { this.dailyLimit = dailyLimit; }
        public long getMonthlyLimit() { return monthlyLimit; }
        public void setMonthlyLimit(long monthlyLimit) { this.monthlyLimit = monthlyLimit; }
    }
}
