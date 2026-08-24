package com.sentinelgateway.gateway.metrics;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Lightweight in-memory counters for gateway request metrics.
 *
 * Tracks:
 * - Total inflight requests (gauge — incremented on entry, decremented on completion)
 * - Total completed requests (monotonic counter)
 * - Per-route completed request counts (monotonic counter per routeId)
 *
 * Thread-safe via {@link AtomicLong}. All state is ephemeral (lost on restart).
 * For durable metrics, use the Micrometer/Prometheus endpoint at /actuator/prometheus.
 */
@Component
public class RequestMetricsRegistry {

    private final AtomicLong inflightRequests = new AtomicLong(0);
    private final AtomicLong totalCompleted   = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> perRouteCompleted = new ConcurrentHashMap<>();

    public void requestStarted() {
        inflightRequests.incrementAndGet();
    }

    public void requestCompleted(String routeId) {
        inflightRequests.decrementAndGet();
        totalCompleted.incrementAndGet();
        if (routeId != null && !routeId.isBlank()) {
            perRouteCompleted.computeIfAbsent(routeId, k -> new AtomicLong(0))
                    .incrementAndGet();
        }
    }

    public long getInflightRequests() {
        return inflightRequests.get();
    }

    public long getTotalCompleted() {
        return totalCompleted.get();
    }

    /** Snapshot of per-route completed counts. */
    public Map<String, Long> getPerRouteCompleted() {
        return perRouteCompleted.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    public void reset() {
        inflightRequests.set(0);
        totalCompleted.set(0);
        perRouteCompleted.clear();
    }
}
