package com.sentinelgateway.gateway.pathlength;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for request path/query length guard (Phase 43).
 *
 * sentinel.path-length:
 *   enabled: true
 *   max-path-length: 2048   # reject if request path exceeds this many characters
 *   max-query-length: 4096  # reject if query string exceeds this many characters
 *   max-records: 100        # ring-buffer capacity for rejection history
 */
@Component
@ConfigurationProperties(prefix = "sentinel.path-length")
public class PathLengthProperties {

    private boolean enabled = true;
    private int maxPathLength = 2048;
    private int maxQueryLength = 4096;
    private int maxRecords = 100;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxPathLength() { return maxPathLength; }
    public void setMaxPathLength(int maxPathLength) { this.maxPathLength = maxPathLength; }

    public int getMaxQueryLength() { return maxQueryLength; }
    public void setMaxQueryLength(int maxQueryLength) { this.maxQueryLength = maxQueryLength; }

    public int getMaxRecords() { return maxRecords; }
    public void setMaxRecords(int maxRecords) { this.maxRecords = maxRecords; }
}
