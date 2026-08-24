package com.sentinelgateway.gateway.concurrent;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks in-flight request count, peak concurrency, and total served requests (Phase 41).
 *
 * Thread-safe via atomic operations. Peak is updated with a spin-loop CAS so it never
 * under-counts, even under concurrent increments.
 */
@Component
public class ConcurrentRequestRegistry {

    private final AtomicInteger current = new AtomicInteger(0);
    private final AtomicInteger peak = new AtomicInteger(0);
    private final AtomicLong totalCompleted = new AtomicLong(0);

    public void increment() {
        int now = current.incrementAndGet();
        int prev = peak.get();
        while (now > prev) {
            if (peak.compareAndSet(prev, now)) break;
            prev = peak.get();
        }
    }

    public void decrement() {
        current.decrementAndGet();
        totalCompleted.incrementAndGet();
    }

    public int getCurrent() {
        return current.get();
    }

    public int getPeak() {
        return peak.get();
    }

    public long getTotalCompleted() {
        return totalCompleted.get();
    }

    public void resetPeak() {
        peak.set(current.get());
        totalCompleted.set(0);
    }
}
