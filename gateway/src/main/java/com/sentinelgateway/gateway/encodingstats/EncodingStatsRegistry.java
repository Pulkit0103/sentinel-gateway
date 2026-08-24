package com.sentinelgateway.gateway.encodingstats;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-encoding request count registry (Phase 54).
 *
 * Tracks Content-Encoding header distribution (gzip, deflate, br, identity, none).
 * "none" is recorded when the header is absent.
 */
@Component
public class EncodingStatsRegistry {

    private final ConcurrentHashMap<String, AtomicLong> contentEncodingCounts = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();

    public void record(String contentEncoding) {
        String key = contentEncoding != null && !contentEncoding.isBlank()
                ? contentEncoding.trim().toLowerCase()
                : "none";
        contentEncodingCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        total.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<String, Long> perEncoding = new LinkedHashMap<>();
        contentEncodingCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> perEncoding.put(e.getKey(), e.getValue().get()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total.get());
        result.put("encodings", Collections.unmodifiableMap(perEncoding));
        return Collections.unmodifiableMap(result);
    }

    public void reset() {
        contentEncodingCounts.clear();
        total.set(0);
    }

    public long getTotal() {
        return total.get();
    }
}
