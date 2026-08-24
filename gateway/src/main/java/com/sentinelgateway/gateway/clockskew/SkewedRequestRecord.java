package com.sentinelgateway.gateway.clockskew;

import java.time.Instant;

/**
 * Immutable snapshot of a single skewed-timestamp request (Phase 50).
 */
public record SkewedRequestRecord(
        Instant detectedAt,
        String method,
        String path,
        long clientTimestamp,
        long skewSeconds
) {}
