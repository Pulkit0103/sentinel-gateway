package com.sentinelgateway.gateway.sanitizer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Configuration for the request header sanitizer (Phase 32).
 *
 * sentinel.request-sanitizer:
 *   enabled: true
 *   max-header-value-length: 8192   # reject if any non-skipped header value exceeds this byte count
 *   block-null-bytes: true           # reject if any non-skipped header value contains a null byte (\0)
 *
 * Standard headers like Authorization, User-Agent, Accept, Content-Type are skipped
 * because they can legitimately be long (e.g., JWT bearer tokens).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.request-sanitizer")
public class RequestSanitizerProperties {

    /** Headers that are never length-checked (case-insensitive match). */
    private static final Set<String> SKIP_HEADERS = Set.of(
            "authorization", "cookie", "user-agent", "host",
            "accept", "accept-encoding", "accept-language",
            "content-type", "content-length", "connection",
            "transfer-encoding", "cache-control",
            // gateway-generated headers
            "x-request-id", "traceparent", "tracestate", "baggage",
            "x-forwarded-for", "x-forwarded-proto", "forwarded"
    );

    private boolean enabled = true;
    private int maxHeaderValueLength = 8192;
    private boolean blockNullBytes = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxHeaderValueLength() { return maxHeaderValueLength; }
    public void setMaxHeaderValueLength(int maxHeaderValueLength) { this.maxHeaderValueLength = maxHeaderValueLength; }

    public boolean isBlockNullBytes() { return blockNullBytes; }
    public void setBlockNullBytes(boolean blockNullBytes) { this.blockNullBytes = blockNullBytes; }

    /** Returns true if the given header name should be skipped from checks. */
    public boolean isSkipped(String headerName) {
        return headerName != null && SKIP_HEADERS.contains(headerName.toLowerCase());
    }
}
