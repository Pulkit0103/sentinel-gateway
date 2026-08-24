package com.sentinelgateway.gateway.pathlength;

import java.time.Instant;

/**
 * Immutable snapshot of a request rejected for exceeding path or query length limits (Phase 43).
 */
public record PathLengthRecord(
        Instant timestamp,
        String method,
        String path,
        int pathLength,
        int queryLength,
        String violation  // "path" or "query"
) {}
