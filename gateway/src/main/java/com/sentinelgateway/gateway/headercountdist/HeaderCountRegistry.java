package com.sentinelgateway.gateway.headercountdist;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Request header count distribution registry (Phase 58).
 *
 * Buckets:
 *   low      — 1-5 headers
 *   normal   — 6-15 headers
 *   elevated — 16-30 headers
 *   high     — 31+ headers
 */
@Component
public class HeaderCountRegistry {

    private final ConcurrentHashMap<String, AtomicLong> buckets = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong maxSeen = new AtomicLong();

    public void record(int headerCount) {
        String bucket = classify(headerCount);
        buckets.computeIfAbsent(bucket, k -> new AtomicLong()).incrementAndGet();
        total.incrementAndGet();
        long current;
        do {
            current = maxSeen.get();
        } while (headerCount > current && !maxSeen.compareAndSet(current, headerCount));
    }

    public Map<String, Object> snapshot() {
        Map<String, Long> dist = new LinkedHashMap<>();
        for (String key : new String[]{"low", "normal", "elevated", "high"}) {
            AtomicLong counter = buckets.get(key);
            dist.put(key, counter != null ? counter.get() : 0L);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total.get());
        result.put("maxSeen", maxSeen.get());
        result.put("buckets", Collections.unmodifiableMap(dist));
        return Collections.unmodifiableMap(result);
    }

    public void reset() {
        buckets.clear();
        total.set(0);
        maxSeen.set(0);
    }

    public long getTotal() { return total.get(); }
    public long getMaxSeen() { return maxSeen.get(); }

    static String classify(int count) {
        if (count <= 5)  return "low";
        if (count <= 15) return "normal";
        if (count <= 30) return "elevated";
        return "high";
    }
}
