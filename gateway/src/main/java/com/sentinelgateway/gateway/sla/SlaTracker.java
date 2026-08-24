package com.sentinelgateway.gateway.sla;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory per-route SLA statistics.
 *
 * Tracks per route:
 * - Total request count
 * - Breach count (requests that exceeded the SLA target)
 * - Sum of all response times (for mean calculation)
 * - Max response time observed
 *
 * Thread-safe via ConcurrentHashMap + AtomicLong. State is ephemeral.
 */
@Component
public class SlaTracker {

    private final ConcurrentHashMap<String, RouteStats> stats = new ConcurrentHashMap<>();

    public void record(String routeId, long durationMs, long targetMs) {
        RouteStats rs = stats.computeIfAbsent(routeId, k -> new RouteStats());
        rs.totalRequests.incrementAndGet();
        rs.totalDurationMs.addAndGet(durationMs);
        rs.maxDurationMs.accumulateAndGet(durationMs, Math::max);
        if (durationMs > targetMs) {
            rs.breachCount.incrementAndGet();
        }
    }

    public Map<String, SlaSnapshot> snapshots() {
        return stats.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toSnapshot(e.getKey())));
    }

    public void reset() {
        stats.clear();
    }

    private static class RouteStats {
        final AtomicLong totalRequests  = new AtomicLong(0);
        final AtomicLong breachCount    = new AtomicLong(0);
        final AtomicLong totalDurationMs= new AtomicLong(0);
        final AtomicLong maxDurationMs  = new AtomicLong(0);

        SlaSnapshot toSnapshot(String routeId) {
            long total = totalRequests.get();
            long breaches = breachCount.get();
            long mean = total > 0 ? totalDurationMs.get() / total : 0;
            double breachRate = total > 0 ? (double) breaches / total * 100.0 : 0.0;
            return new SlaSnapshot(routeId, total, breaches, mean, maxDurationMs.get(), breachRate);
        }
    }

    public record SlaSnapshot(
            String routeId,
            long totalRequests,
            long breachCount,
            long meanDurationMs,
            long maxDurationMs,
            double breachRatePct
    ) {}
}
