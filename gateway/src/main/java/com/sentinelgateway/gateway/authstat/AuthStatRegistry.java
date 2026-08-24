package com.sentinelgateway.gateway.authstat;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AuthStatRegistry {

    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong(0);

    static String classify(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) return "none";
        String lower = authHeader.trim().toLowerCase();
        if (lower.startsWith("bearer ")) return "bearer";
        if (lower.startsWith("apikey ") || lower.startsWith("api-key ")) return "apikey";
        if (lower.startsWith("basic ")) return "basic";
        return "other";
    }

    public void record(String authHeader) {
        String type = classify(authHeader);
        counts.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();
        total.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<String, Long> dist = new LinkedHashMap<>();
        for (String key : new String[]{"none", "bearer", "apikey", "basic", "other"}) {
            AtomicLong c = counts.get(key);
            dist.put(key, c == null ? 0L : c.get());
        }
        return Map.of("total", total.get(), "types", dist);
    }

    public void reset() {
        counts.clear();
        total.set(0);
    }
}
