package com.sentinelgateway.gateway.ipfilter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Phase 7: Per-Route IP Allowlist/Denylist.
 *
 * Per-route IP lists are stored in the {@code routes} table (allowed_ips, blocked_ips columns).
 * This properties class holds the global enabled flag.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.ip-filter")
public class IpFilterProperties {

    /** Global kill-switch: when false, IP filtering is skipped entirely. */
    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
