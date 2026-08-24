package com.sentinelgateway.gateway.ipcounter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for per-IP request count tracking (Phase 49).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.ip-counter")
public class IpCounterProperties {

    private boolean enabled = true;
    private int topN = 20;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getTopN() { return topN; }
    public void setTopN(int topN) { this.topN = topN; }
}
