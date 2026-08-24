package com.sentinelgateway.gateway.slowrequest;

import java.time.Instant;

/**
 * Immutable snapshot of a single slow request (Phase 33).
 */
public record SlowRequestRecord(
        Instant timestamp,
        String method,
        String path,
        long durationMs,
        int status
) {}
