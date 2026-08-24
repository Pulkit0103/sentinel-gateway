package com.sentinelgateway.gateway.requestsize;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the request body size guard (Phase 37).
 *
 * sentinel.request-size:
 *   enabled: true
 *   max-body-bytes: 10485760   # 10 MB default; requests with larger Content-Length are rejected 413
 *   max-records: 100           # ring-buffer capacity for oversized-request history
 */
@Component
@ConfigurationProperties(prefix = "sentinel.request-size")
public class RequestSizeProperties {

    private boolean enabled = true;
    private long maxBodyBytes = 10_485_760L; // 10 MB
    private int maxRecords = 100;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getMaxBodyBytes() { return maxBodyBytes; }
    public void setMaxBodyBytes(long maxBodyBytes) { this.maxBodyBytes = maxBodyBytes; }

    public int getMaxRecords() { return maxRecords; }
    public void setMaxRecords(int maxRecords) { this.maxRecords = maxRecords; }
}
