package com.sentinelgateway.gateway.contenttype;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-Content-Type response count registry (Phase 47).
 *
 * Normalises Content-Type by stripping parameters (e.g. "application/json;charset=UTF-8"
 * → "application/json") so variants are bucketed together.
 */
@Component
public class ContentTypeRegistry {

    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();

    public void record(String contentType) {
        String normalised = normalise(contentType);
        counts.computeIfAbsent(normalised, k -> new AtomicLong()).incrementAndGet();
        total.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<String, Long> perType = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> perType.put(e.getKey(), e.getValue().get()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total.get());
        result.put("types", Collections.unmodifiableMap(perType));
        return Collections.unmodifiableMap(result);
    }

    public void reset() {
        counts.clear();
        total.set(0);
    }

    static String normalise(String contentType) {
        if (contentType == null || contentType.isBlank()) return "unknown";
        int semi = contentType.indexOf(';');
        return (semi >= 0 ? contentType.substring(0, semi) : contentType).trim().toLowerCase();
    }
}
