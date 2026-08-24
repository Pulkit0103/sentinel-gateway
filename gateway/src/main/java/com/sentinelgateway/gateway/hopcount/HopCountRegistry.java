package com.sentinelgateway.gateway.hopcount;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics registry for the hop count guard (Phase 44).
 *
 * Tracks per-hop-count distribution plus total processed/rejected counts.
 */
@Component
public class HopCountRegistry {

    private final AtomicLong totalProcessed = new AtomicLong();
    private final AtomicLong totalRejected = new AtomicLong();
    private final ConcurrentHashMap<Integer, AtomicLong> distribution = new ConcurrentHashMap<>();

    public void recordProcessed(int hopCount) {
        totalProcessed.incrementAndGet();
        distribution.computeIfAbsent(hopCount, k -> new AtomicLong()).incrementAndGet();
    }

    public void recordRejected(int hopCount) {
        totalRejected.incrementAndGet();
        distribution.computeIfAbsent(hopCount, k -> new AtomicLong()).incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<Integer, Long> dist = new LinkedHashMap<>();
        distribution.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> dist.put(e.getKey(), e.getValue().get()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalProcessed", totalProcessed.get());
        result.put("totalRejected", totalRejected.get());
        result.put("distribution", Collections.unmodifiableMap(dist));
        return Collections.unmodifiableMap(result);
    }

    public void reset() {
        totalProcessed.set(0);
        totalRejected.set(0);
        distribution.clear();
    }
}
