package com.sentinelgateway.gateway.cachettl;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Response Cache-Control TTL bucket distribution registry (Phase 57).
 *
 * Buckets:
 *   no-cache  — no-store, no-cache, max-age=0, or no Cache-Control header
 *   short     — max-age 1–60 s
 *   medium    — max-age 61–3600 s (1 min – 1 hr)
 *   long      — max-age > 3600 s (> 1 hr)
 */
@Component
public class CacheTtlRegistry {

    private final ConcurrentHashMap<String, AtomicLong> buckets = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();

    public void record(String cacheControl) {
        String bucket = classify(cacheControl);
        buckets.computeIfAbsent(bucket, k -> new AtomicLong()).incrementAndGet();
        total.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<String, Long> dist = new LinkedHashMap<>();
        for (String key : new String[]{"no-cache", "short", "medium", "long"}) {
            AtomicLong counter = buckets.get(key);
            dist.put(key, counter != null ? counter.get() : 0L);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total.get());
        result.put("buckets", Collections.unmodifiableMap(dist));
        return Collections.unmodifiableMap(result);
    }

    public void reset() {
        buckets.clear();
        total.set(0);
    }

    public long getTotal() { return total.get(); }

    static String classify(String cacheControl) {
        if (cacheControl == null || cacheControl.isBlank()) return "no-cache";
        String cc = cacheControl.toLowerCase();
        if (cc.contains("no-store") || cc.contains("no-cache")) return "no-cache";

        // Parse max-age=N
        int idx = cc.indexOf("max-age=");
        if (idx < 0) return "no-cache";
        try {
            String rest = cc.substring(idx + 8).replaceAll("[^0-9].*", "");
            long maxAge = Long.parseLong(rest.trim());
            if (maxAge <= 0)   return "no-cache";
            if (maxAge <= 60)  return "short";
            if (maxAge <= 3600) return "medium";
            return "long";
        } catch (NumberFormatException e) {
            return "no-cache";
        }
    }
}
