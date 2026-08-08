package com.sentinelgateway.gateway.audit;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Immutable audit record emitted for every security decision at the gateway.
 *
 * Published to Kafka topic {@code sentinel.audit.requests} (or logged when
 * Kafka is unavailable).  All fields are safe for public audit logs — no
 * credentials or PII beyond user ID and tenant ID.
 *
 * Fields:
 *   requestId      — from X-Request-ID header (set by RequestIdFilter)
 *   timestamp      — ISO-8601 UTC instant
 *   clientIp       — originating IP (X-Forwarded-For first value, else remote)
 *   method         — HTTP method
 *   path           — request path (no query string — avoid logging user input)
 *   routeId        — matched gateway route ID; null if request was rejected pre-routing
 *   responseStatus — HTTP status code of the gateway's response
 *   outcome        — human-readable outcome label
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEvent(
        String requestId,
        String timestamp,
        String clientIp,
        String method,
        String path,
        String routeId,
        int responseStatus,
        String outcome
) {

    /** Derive a short outcome label from the HTTP status. */
    public static String outcomeFor(int status) {
        if (status >= 200 && status < 300) return "ALLOWED";
        if (status == 400)                 return "BLOCKED_WAF";
        if (status == 401)                 return "UNAUTHENTICATED";
        if (status == 403)                 return "UNAUTHORIZED";
        if (status == 404)                 return "NOT_FOUND";
        if (status == 429)                 return "RATE_LIMITED";
        if (status >= 500)                 return "ERROR";
        return "UNKNOWN";
    }
}
