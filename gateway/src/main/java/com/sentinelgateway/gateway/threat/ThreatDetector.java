package com.sentinelgateway.gateway.threat;

import org.springframework.web.server.ServerWebExchange;

import java.util.List;

/**
 * Single-responsibility threat pattern detector.
 *
 * Implementations are Spring components discovered automatically and injected
 * as a {@code List<ThreatDetector>} into {@link ThreatDetectionFilter}.
 * New attack categories are added by creating a new {@code @Component}
 * implementation — no existing code changes required.
 *
 * Detection is synchronous and CPU-only; no I/O should be performed here.
 *
 * Contract:
 *   - return an empty list when no threat is found
 *   - return one or more {@link ThreatSignal}s when patterns match
 */
public interface ThreatDetector {

    List<ThreatSignal> detect(ServerWebExchange exchange);
}
