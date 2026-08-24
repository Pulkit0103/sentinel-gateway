package com.sentinelgateway.gateway.latency;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Per-route latency sample registry for computing response-time percentiles (Phase 38).
 *
 * Each route keeps a bounded ring-buffer of the most recent {@code maxSamplesPerRoute} durations (ms).
 * Percentiles are computed on a sorted copy of the buffer at query time — suitable for
 * low-cardinality admin monitoring, not high-throughput Prometheus scraping.
 */
@Component
public class LatencyRegistry {

    private final LatencyProperties properties;
    private final ConcurrentHashMap<String, LinkedBlockingDeque<Long>> routeSamples =
            new ConcurrentHashMap<>();

    public LatencyRegistry(LatencyProperties properties) {
        this.properties = properties;
    }

    public void record(String route, long durationMs) {
        LinkedBlockingDeque<Long> deque = routeSamples.computeIfAbsent(
                route, k -> new LinkedBlockingDeque<>(properties.getMaxSamplesPerRoute()));
        if (!deque.offerLast(durationMs)) {
            deque.pollFirst();
            deque.offerLast(durationMs);
        }
    }

    /**
     * Returns a snapshot map: route → {@link LatencySnapshot} with p50/p95/p99/count.
     */
    public Map<String, LatencySnapshot> snapshots() {
        Map<String, LatencySnapshot> result = new ConcurrentHashMap<>();
        routeSamples.forEach((route, deque) -> {
            List<Long> sorted = new ArrayList<>(deque);
            if (!sorted.isEmpty()) {
                Collections.sort(sorted);
                result.put(route, LatencySnapshot.of(sorted));
            }
        });
        return Collections.unmodifiableMap(result);
    }

    public int routeCount() {
        return routeSamples.size();
    }

    public void reset() {
        routeSamples.clear();
    }
}
