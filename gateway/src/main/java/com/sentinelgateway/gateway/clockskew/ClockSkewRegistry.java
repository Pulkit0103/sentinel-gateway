package com.sentinelgateway.gateway.clockskew;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry for requests detected with significant clock skew (Phase 50).
 *
 * Tracks total requests checked, skew-flagged count, and a ring-buffer of recent skewed records.
 */
@Component
public class ClockSkewRegistry {

    private final AtomicLong totalChecked = new AtomicLong();
    private final AtomicLong totalSkewed = new AtomicLong();
    private volatile int maxRecords = 100;
    private final LinkedBlockingDeque<SkewedRequestRecord> recentSkewed = new LinkedBlockingDeque<>(1000);

    public void setMaxRecords(int maxRecords) {
        this.maxRecords = maxRecords;
    }

    public void recordChecked() {
        totalChecked.incrementAndGet();
    }

    public void recordSkewed(SkewedRequestRecord rec) {
        totalSkewed.incrementAndGet();
        if (recentSkewed.size() >= maxRecords) {
            recentSkewed.pollFirst();
        }
        recentSkewed.offerLast(rec);
    }

    public Map<String, Object> snapshot() {
        List<SkewedRequestRecord> recent = new ArrayList<>(recentSkewed);
        Collections.reverse(recent);
        return Map.of(
                "totalChecked", totalChecked.get(),
                "totalSkewed", totalSkewed.get(),
                "recentSkewed", recent
        );
    }

    public void reset() {
        totalChecked.set(0);
        totalSkewed.set(0);
        recentSkewed.clear();
    }

    public long getTotalSkewed() {
        return totalSkewed.get();
    }
}
