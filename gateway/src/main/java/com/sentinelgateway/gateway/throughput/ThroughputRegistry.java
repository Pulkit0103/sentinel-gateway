package com.sentinelgateway.gateway.throughput;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry for request throughput rate (RPS) monitoring (Phase 66).
 *
 * Keeps a sliding deque of request timestamps. Snapshot computes average
 * RPS for 1-second, 10-second, and 60-second windows at query time.
 * No background threads — windows derived from stored timestamps.
 */
@Component
public class ThroughputRegistry {

    private final ConcurrentLinkedDeque<Long> timestamps = new ConcurrentLinkedDeque<>();
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong peakRps = new AtomicLong();

    public void record() {
        long now = System.currentTimeMillis();
        timestamps.addLast(now);
        total.incrementAndGet();
        updatePeak(now);
    }

    private void updatePeak(long now) {
        long cutoff = now - 1000L;
        long recent = timestamps.stream().filter(t -> t >= cutoff).count();
        long current;
        do { current = peakRps.get(); }
        while (recent > current && !peakRps.compareAndSet(current, recent));
    }

    public long getTotal() { return total.get(); }
    public long getPeakRps() { return peakRps.get(); }

    public Map<String, Object> snapshot() {
        long now = System.currentTimeMillis();

        long count1s  = timestamps.stream().filter(t -> t >= now - 1_000L).count();
        long count10s = timestamps.stream().filter(t -> t >= now - 10_000L).count();
        long count60s = timestamps.stream().filter(t -> t >= now - 60_000L).count();

        double avg1s  = (double) count1s;
        double avg10s = count10s / 10.0;
        double avg60s = count60s / 60.0;

        return Map.of(
                "total", total.get(),
                "peakRps", peakRps.get(),
                "rps1s",  round1(avg1s),
                "rps10s", round1(avg10s),
                "rps60s", round1(avg60s)
        );
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    public void reset() {
        timestamps.clear();
        total.set(0);
        peakRps.set(0);
    }
}
