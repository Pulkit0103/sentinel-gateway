package com.sentinelgateway.gateway.threat;

/**
 * The action taken by the gateway in response to a computed risk score.
 *
 * Thresholds are configurable via {@link ThreatDetectionProperties}.
 *
 * ALLOW          — score below log threshold; pass through normally
 * LOG            — score in log range; pass through but emit an INFO log
 * BLOCK          — score in block range; reject with 400 Bad Request
 * BLOCK_AND_ALERT — score at or above alert threshold; reject and emit an ERROR log
 */
public enum ThreatAction {
    ALLOW,
    LOG,
    BLOCK,
    BLOCK_AND_ALERT
}
