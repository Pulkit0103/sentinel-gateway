package com.sentinelgateway.gateway.methodstats;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-HTTP-method request count registry (Phase 46).
 *
 * Thread-safe via AtomicLong. Tracks total plus per-method breakdown.
 */
@Component
public class MethodRegistry {

    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();

    public void record(String method) {
        counts.computeIfAbsent(method, k -> new AtomicLong()).incrementAndGet();
        total.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<String, Long> perMethod = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> perMethod.put(e.getKey(), e.getValue().get()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total.get());
        result.put("methods", Collections.unmodifiableMap(perMethod));
        return Collections.unmodifiableMap(result);
    }

    public void reset() {
        counts.clear();
        total.set(0);
    }
}
