package com.sentinelgateway.gateway.protocolstats;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-protocol-version request count registry (Phase 52).
 *
 * Records the HTTP protocol version string (e.g. "HTTP/1.1", "HTTP/2.0") from each request.
 */
@Component
public class ProtocolStatsRegistry {

    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();

    public void record(String version) {
        String key = version != null && !version.isBlank() ? version : "unknown";
        counts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        total.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<String, Long> perVersion = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> perVersion.put(e.getKey(), e.getValue().get()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total.get());
        result.put("versions", Collections.unmodifiableMap(perVersion));
        return Collections.unmodifiableMap(result);
    }

    public void reset() {
        counts.clear();
        total.set(0);
    }

    public long getTotal() {
        return total.get();
    }
}
