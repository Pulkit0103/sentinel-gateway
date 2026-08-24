package com.sentinelgateway.gateway.errorpath;

import java.time.Instant;

/**
 * Snapshot of a single error response event (Phase 56).
 */
public record ErrorPathRecord(
        Instant timestamp,
        String method,
        String path,
        int statusCode
) {}
