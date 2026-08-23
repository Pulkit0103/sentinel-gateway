package com.sentinelgateway.gateway.threat.detectors;

import org.springframework.web.server.ServerWebExchange;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Shared utilities for threat detectors.
 */
final class DetectorUtils {

    private DetectorUtils() {}

    /**
     * Returns the decoded URL target (path + "?" + query) for pattern matching.
     *
     * URL-decoding happens before matching so that percent-encoded attack vectors
     * (e.g. {@code %3Cscript%3E} for {@code <script>}) are caught by the same
     * patterns as their unencoded equivalents.  {@code +} in query strings is
     * decoded as space per the {@code application/x-www-form-urlencoded} convention.
     *
     * Malformed percent sequences fall back to the raw string rather than throwing.
     */
    static String decodedTarget(ServerWebExchange exchange) {
        var uri = exchange.getRequest().getURI();
        String path  = decode(uri.getRawPath());
        String query = decode(uri.getRawQuery());
        return query.isBlank() ? path : path + "?" + query;
    }

    static String truncate(String s) {
        return s.length() > 100 ? s.substring(0, 100) + "..." : s;
    }

    private static String decode(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return raw; // malformed encoding — use raw string
        }
    }
}
