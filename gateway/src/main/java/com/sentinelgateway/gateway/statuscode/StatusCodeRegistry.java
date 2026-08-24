package com.sentinelgateway.gateway.statuscode;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory counter for HTTP response status codes (Phase 40).
 *
 * Tracks per-code counts plus four range buckets (2xx, 3xx, 4xx, 5xx).
 * Thread-safe via AtomicLong. State is ephemeral.
 */
@Component
public class StatusCodeRegistry {

    private final ConcurrentHashMap<Integer, AtomicLong> perCode = new ConcurrentHashMap<>();

    private final AtomicLong count2xx = new AtomicLong();
    private final AtomicLong count3xx = new AtomicLong();
    private final AtomicLong count4xx = new AtomicLong();
    private final AtomicLong count5xx = new AtomicLong();
    private final AtomicLong countOther = new AtomicLong();

    public void record(int statusCode) {
        perCode.computeIfAbsent(statusCode, k -> new AtomicLong()).incrementAndGet();
        if (statusCode >= 200 && statusCode < 300) count2xx.incrementAndGet();
        else if (statusCode >= 300 && statusCode < 400) count3xx.incrementAndGet();
        else if (statusCode >= 400 && statusCode < 500) count4xx.incrementAndGet();
        else if (statusCode >= 500 && statusCode < 600) count5xx.incrementAndGet();
        else countOther.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<Integer, Long> codes = new LinkedHashMap<>();
        perCode.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> codes.put(e.getKey(), e.getValue().get()));

        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("2xx", count2xx.get());
        buckets.put("3xx", count3xx.get());
        buckets.put("4xx", count4xx.get());
        buckets.put("5xx", count5xx.get());
        buckets.put("other", countOther.get());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", codes.values().stream().mapToLong(Long::longValue).sum());
        result.put("buckets", Collections.unmodifiableMap(buckets));
        result.put("codes", Collections.unmodifiableMap(codes));
        return Collections.unmodifiableMap(result);
    }

    public void reset() {
        perCode.clear();
        count2xx.set(0);
        count3xx.set(0);
        count4xx.set(0);
        count5xx.set(0);
        countOther.set(0);
    }
}
