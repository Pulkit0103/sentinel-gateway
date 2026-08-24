package com.sentinelgateway.gateway.slowrequest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for slow request detection (Phase 33).
 *
 * sentinel.slow-request:
 *   enabled: true
 *   threshold-ms: 2000   # requests taking longer than this are recorded
 *   max-records: 100     # ring-buffer capacity for slow request entries
 */
@Component
@ConfigurationProperties(prefix = "sentinel.slow-request")
public class SlowRequestProperties {

    private boolean enabled = true;
    private long thresholdMs = 2000;
    private int maxRecords = 100;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getThresholdMs() { return thresholdMs; }
    public void setThresholdMs(long thresholdMs) { this.thresholdMs = thresholdMs; }

    public int getMaxRecords() { return maxRecords; }
    public void setMaxRecords(int maxRecords) { this.maxRecords = maxRecords; }
}
