package com.sentinelgateway.gateway.refererstat;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Registry for Referer header domain distribution (Phase 63).
 *
 * Extracts the host portion from the Referer URL. Absent/unparseable
 * Referer values are counted under "direct" and "unknown" respectively.
 */
@Component
public class RefererStatRegistry {

    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();

    public void record(String refererHeader) {
        String domain = extractDomain(refererHeader);
        counts.computeIfAbsent(domain, k -> new AtomicLong()).incrementAndGet();
        total.incrementAndGet();
    }

    static String extractDomain(String referer) {
        if (referer == null || referer.isBlank()) return "direct";
        try {
            URI uri = URI.create(referer.trim());
            String host = uri.getHost();
            return (host != null && !host.isBlank()) ? host.toLowerCase() : "unknown";
        } catch (IllegalArgumentException e) {
            return "unknown";
        }
    }

    public long getTotal() { return total.get(); }

    public Map<String, Object> snapshot(int topN) {
        List<Map<String, Object>> top = counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(topN)
                .map(e -> Map.<String, Object>of("domain", e.getKey(), "requests", e.getValue().get()))
                .collect(Collectors.toList());
        return Map.of("total", total.get(), "topDomains", top);
    }

    public void reset() {
        counts.clear();
        total.set(0);
    }
}
