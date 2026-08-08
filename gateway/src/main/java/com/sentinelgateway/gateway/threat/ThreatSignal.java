package com.sentinelgateway.gateway.threat;

/**
 * A single detected threat indicator.
 *
 * Returned by {@link ThreatDetector} implementations when a pattern matches.
 * Multiple signals can be emitted per request (one per matching pattern).
 * Their scores are summed by {@link ThreatRiskScorer} to produce the overall
 * risk level.
 */
public record ThreatSignal(ThreatPattern pattern, String matchedValue) {

    /** Risk contribution from this signal. */
    public int score() {
        return pattern.baseScore();
    }

    @Override
    public String toString() {
        return pattern.name() + "(score=" + score() + ", match='" + matchedValue + "')";
    }
}
