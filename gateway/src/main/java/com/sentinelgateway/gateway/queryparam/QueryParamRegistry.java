package com.sentinelgateway.gateway.queryparam;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry for request query-parameter count distribution (Phase 60).
 *
 * Buckets:
 *   zero     — 0 parameters
 *   few      — 1-3 parameters
 *   moderate — 4-8 parameters
 *   many     — 9+ parameters
 */
@Component
public class QueryParamRegistry {

    private final ConcurrentHashMap<String, AtomicLong> buckets = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong maxSeen = new AtomicLong();

    public QueryParamRegistry() {
        for (String b : new String[]{"zero", "few", "moderate", "many"}) {
            buckets.put(b, new AtomicLong());
        }
    }

    public void record(int count) {
        total.incrementAndGet();
        buckets.get(classify(count)).incrementAndGet();

        long current;
        do { current = maxSeen.get(); }
        while (count > current && !maxSeen.compareAndSet(current, count));
    }

    public static String classify(int count) {
        if (count == 0) return "zero";
        if (count <= 3)  return "few";
        if (count <= 8)  return "moderate";
        return "many";
    }

    public long getTotal() { return total.get(); }
    public long getMaxSeen() { return maxSeen.get(); }

    public Map<String, Object> snapshot() {
        Map<String, Long> snap = new LinkedHashMap<>();
        snap.put("zero",     buckets.get("zero").get());
        snap.put("few",      buckets.get("few").get());
        snap.put("moderate", buckets.get("moderate").get());
        snap.put("many",     buckets.get("many").get());
        return Map.of("total", total.get(), "maxSeen", maxSeen.get(), "buckets", snap);
    }

    public void reset() {
        buckets.values().forEach(c -> c.set(0));
        total.set(0);
        maxSeen.set(0);
    }
}
