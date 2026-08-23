package com.sentinelgateway.gateway.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the analytics subsystem.
 *
 * Bound from the {@code sentinel.analytics} prefix in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.analytics")
public class AnalyticsProperties {

    /** When false the NoOpAnalyticsService is used and no Redis writes are made. */
    private boolean enabled = true;

    /** Number of days to retain analytics data in Redis (TTL). */
    private int retentionDays = 7;

    /** Number of top routes/tenants to include in summary responses. */
    private int topN = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getTopN() {
        return topN;
    }

    public void setTopN(int topN) {
        this.topN = topN;
    }
}
