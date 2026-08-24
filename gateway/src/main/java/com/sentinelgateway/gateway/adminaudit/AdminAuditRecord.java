package com.sentinelgateway.gateway.adminaudit;

import java.time.Instant;

/**
 * Immutable snapshot of a single admin API operation (Phase 31).
 */
public record AdminAuditRecord(
        Instant timestamp,
        String subject,
        String method,
        String path,
        int status
) {}
