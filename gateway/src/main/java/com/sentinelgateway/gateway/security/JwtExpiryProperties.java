package com.sentinelgateway.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for JWT expiry warning headers (Phase 36).
 *
 * sentinel.jwt-expiry-warning:
 *   enabled: true
 *   warning-window-seconds: 300   # add warning header when JWT expires within this many seconds
 *   header-name: X-JWT-Expires-In # name of the response header added when JWT is expiring soon
 */
@Component
@ConfigurationProperties(prefix = "sentinel.jwt-expiry-warning")
public class JwtExpiryProperties {

    private boolean enabled = true;
    private long warningWindowSeconds = 300;
    private String headerName = "X-JWT-Expires-In";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getWarningWindowSeconds() { return warningWindowSeconds; }
    public void setWarningWindowSeconds(long warningWindowSeconds) { this.warningWindowSeconds = warningWindowSeconds; }

    public String getHeaderName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }
}
