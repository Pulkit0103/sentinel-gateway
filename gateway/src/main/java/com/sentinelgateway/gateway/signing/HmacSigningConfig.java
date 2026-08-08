package com.sentinelgateway.gateway.signing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for HMAC request signing.
 *
 * sentinel.request-signing:
 *   enabled: true
 *   timestamp-tolerance-seconds: 300  # 5 minute window
 *   nonce-ttl-seconds: 600            # nonce replay prevention TTL
 *   shared-secret: ${HMAC_SHARED_SECRET:change-me-in-production}
 */
@Component
@ConfigurationProperties(prefix = "sentinel.request-signing")
public class HmacSigningConfig {

    private boolean enabled = false;
    private long timestampToleranceSeconds = 300;
    private long nonceTtlSeconds = 600;
    private String sharedSecret = "change-me-in-production";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getTimestampToleranceSeconds() { return timestampToleranceSeconds; }
    public void setTimestampToleranceSeconds(long timestampToleranceSeconds) { this.timestampToleranceSeconds = timestampToleranceSeconds; }
    public long getNonceTtlSeconds() { return nonceTtlSeconds; }
    public void setNonceTtlSeconds(long nonceTtlSeconds) { this.nonceTtlSeconds = nonceTtlSeconds; }
    public String getSharedSecret() { return sharedSecret; }
    public void setSharedSecret(String sharedSecret) { this.sharedSecret = sharedSecret; }
}
