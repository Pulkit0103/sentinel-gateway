package com.sentinelgateway.gateway.responseheadersize;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Response header total-size distribution registry (Phase 59).
 *
 * Measures the sum of (header-name bytes + header-value bytes) across all response headers.
 * Buckets:
 *   small    — 0-512 bytes
 *   medium   — 513-2048 bytes
 *   large    — 2049-8192 bytes
 *   oversized — > 8192 bytes
 */
@Component
public class ResponseHeaderSizeRegistry {

    private final ConcurrentHashMap<String, AtomicLong> buckets = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong maxSeen = new AtomicLong();

    public void record(long sizeBytes) {
        String bucket = classify(sizeBytes);
        buckets.computeIfAbsent(bucket, k -> new AtomicLong()).incrementAndGet();
        total.incrementAndGet();
        long current;
        do {
            current = maxSeen.get();
        } while (sizeBytes > current && !maxSeen.compareAndSet(current, sizeBytes));
    }

    public Map<String, Object> snapshot() {
        Map<String, Long> dist = new LinkedHashMap<>();
        for (String key : new String[]{"small", "medium", "large", "oversized"}) {
            AtomicLong counter = buckets.get(key);
            dist.put(key, counter != null ? counter.get() : 0L);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total.get());
        result.put("maxSeenBytes", maxSeen.get());
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

    static String classify(long sizeBytes) {
        if (sizeBytes <= 512)  return "small";
        if (sizeBytes <= 2048) return "medium";
        if (sizeBytes <= 8192) return "large";
        return "oversized";
    }

    static long measureHeaders(org.springframework.http.HttpHeaders headers) {
        long size = 0;
        for (Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
            int nameLen = entry.getKey().length();
            for (String value : entry.getValue()) {
                size += nameLen + value.length() + 4; // ": " + "\r\n"
            }
        }
        return size;
    }
}
