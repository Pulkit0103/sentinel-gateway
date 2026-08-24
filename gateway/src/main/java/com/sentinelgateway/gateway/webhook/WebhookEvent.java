package com.sentinelgateway.gateway.webhook;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable record describing a significant gateway event to be delivered to
 * external systems via the webhook subsystem.
 *
 * <p>Known event types:
 * <ul>
 *   <li>{@code ROUTE_BLOCKED} — IP filter returned 403</li>
 *   <li>{@code TOKEN_REVOKED} — JWT revocation filter returned 401</li>
 *   <li>{@code THREAT_DETECTED} — threat detection filter blocked a request</li>
 *   <li>{@code QUOTA_EXCEEDED} — quota filter rejected a request</li>
 * </ul>
 */
public record WebhookEvent(
        String eventType,
        String requestId,
        String clientIp,
        String path,
        String routeId,
        Instant timestamp,
        Map<String, Object> metadata
) {
}
