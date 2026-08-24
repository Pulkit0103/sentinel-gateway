package com.sentinelgateway.gateway.pathdepth;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry for URL path-depth distribution (Phase 62).
 *
 * Depth = number of non-empty path segments in the URI path.
 *   /          → 0  (root)
 *   /api       → 1  (shallow)
 *   /api/users → 2  (shallow)
 *   /api/users/123          → 3  (moderate)
 *   /api/users/123/orders   → 4  (moderate)
 *   /api/a/b/c/d/e          → 6  (deep)
 *
 * Buckets:
 *   root     — 0 segments
 *   shallow  — 1-2 segments
 *   moderate — 3-4 segments
 *   deep     — 5+ segments
 */
@Component
public class PathDepthRegistry {

    private final ConcurrentHashMap<String, AtomicLong> buckets = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong maxSeen = new AtomicLong();

    public PathDepthRegistry() {
        for (String b : new String[]{"root", "shallow", "moderate", "deep"}) {
            buckets.put(b, new AtomicLong());
        }
    }

    public void record(int depth) {
        total.incrementAndGet();
        buckets.get(classify(depth)).incrementAndGet();

        long current;
        do { current = maxSeen.get(); }
        while (depth > current && !maxSeen.compareAndSet(current, depth));
    }

    public static String classify(int depth) {
        if (depth == 0) return "root";
        if (depth <= 2) return "shallow";
        if (depth <= 4) return "moderate";
        return "deep";
    }

    public static int countSegments(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) return 0;
        int count = 0;
        boolean inSegment = false;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                inSegment = false;
            } else if (!inSegment) {
                inSegment = true;
                count++;
            }
        }
        return count;
    }

    public long getTotal() { return total.get(); }
    public long getMaxSeen() { return maxSeen.get(); }

    public Map<String, Object> snapshot() {
        Map<String, Long> snap = new LinkedHashMap<>();
        snap.put("root",     buckets.get("root").get());
        snap.put("shallow",  buckets.get("shallow").get());
        snap.put("moderate", buckets.get("moderate").get());
        snap.put("deep",     buckets.get("deep").get());
        return Map.of("total", total.get(), "maxSeen", maxSeen.get(), "buckets", snap);
    }

    public void reset() {
        buckets.values().forEach(c -> c.set(0));
        total.set(0);
        maxSeen.set(0);
    }
}
