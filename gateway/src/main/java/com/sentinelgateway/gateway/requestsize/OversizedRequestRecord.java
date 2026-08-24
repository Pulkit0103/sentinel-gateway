package com.sentinelgateway.gateway.requestsize;

import java.time.Instant;

/**
 * Immutable snapshot of a request that was rejected for exceeding the body-size limit (Phase 37).
 */
public record OversizedRequestRecord(
        Instant timestamp,
        String method,
        String path,
        long claimedBytes
) {}
