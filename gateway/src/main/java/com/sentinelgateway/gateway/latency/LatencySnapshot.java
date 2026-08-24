package com.sentinelgateway.gateway.latency;

import java.util.List;

/**
 * Immutable percentile summary computed from a sorted list of latency samples (Phase 38).
 */
public record LatencySnapshot(long p50Ms, long p95Ms, long p99Ms, int sampleCount) {

    static LatencySnapshot of(List<Long> sorted) {
        int n = sorted.size();
        return new LatencySnapshot(
                percentile(sorted, 50),
                percentile(sorted, 95),
                percentile(sorted, 99),
                n
        );
    }

    private static long percentile(List<Long> sorted, int pct) {
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}
