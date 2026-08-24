package com.sentinelgateway.gateway.hopcount;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the hop count guard (Phase 44).
 *
 * sentinel.hop-count:
 *   enabled: true
 *   max-hops: 10   # reject with 400 if X-Forwarded-For contains more than this many IPs
 */
@Component
@ConfigurationProperties(prefix = "sentinel.hop-count")
public class HopCountProperties {

    private boolean enabled = true;
    private int maxHops = 10;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxHops() { return maxHops; }
    public void setMaxHops(int maxHops) { this.maxHops = maxHops; }
}
