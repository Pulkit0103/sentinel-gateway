package com.sentinelgateway.gateway.clockskew;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for request timestamp skew detection (Phase 50).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.clock-skew")
public class ClockSkewProperties {

    private boolean enabled = true;
    /** Header name carrying the client-supplied epoch-second timestamp. */
    private String headerName = "X-Request-Timestamp";
    /** Requests whose timestamp differs from server time by more than this are flagged. */
    private long toleranceSeconds = 300;
    /** Ring-buffer capacity for skewed-request history. */
    private int maxRecords = 100;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getHeaderName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }

    public long getToleranceSeconds() { return toleranceSeconds; }
    public void setToleranceSeconds(long toleranceSeconds) { this.toleranceSeconds = toleranceSeconds; }

    public int getMaxRecords() { return maxRecords; }
    public void setMaxRecords(int maxRecords) { this.maxRecords = maxRecords; }
}
