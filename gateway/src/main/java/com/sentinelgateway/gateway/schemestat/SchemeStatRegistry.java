package com.sentinelgateway.gateway.schemestat;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry for request URI scheme distribution (Phase 61).
 *
 * Tracks raw scheme values (http, https) and normalises unknown/missing schemes
 * to "unknown". Counts are unbounded — new scheme strings create new counters.
 */
@Component
public class SchemeStatRegistry {

    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();

    public void record(String scheme) {
        String key = (scheme == null || scheme.isBlank()) ? "unknown" : scheme.toLowerCase();
        counts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        total.incrementAndGet();
    }

    public long getTotal() { return total.get(); }

    public Map<String, Object> snapshot() {
        Map<String, Long> snap = new java.util.LinkedHashMap<>();
        counts.forEach((k, v) -> snap.put(k, v.get()));
        return Map.of("total", total.get(), "schemes", snap);
    }

    public void reset() {
        counts.clear();
        total.set(0);
    }
}
