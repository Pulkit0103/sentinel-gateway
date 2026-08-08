package com.sentinelgateway.gateway.quota;

import reactor.core.publisher.Mono;

/**
 * Contract for tenant quota enforcement.
 *
 * Distinct from per-second rate limiting: quotas track cumulative usage over
 * daily or monthly windows and are designed to enforce billing/contract limits.
 *
 * @param tenantId the tenant to check and increment
 * @param period   DAILY or MONTHLY
 */
public interface QuotaChecker {

    Mono<QuotaResult> checkAndIncrement(String tenantId, QuotaPeriod period);
}
