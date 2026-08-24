package com.sentinelgateway.gateway.ipcounter;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Per-IP request count registry (Phase 49).
 *
 * Maintains an unbounded map of IP → count (IPs are naturally bounded in practice).
 * snapshot(topN) returns the top-N IPs ordered by count descending.
 */
@Component
public class IpCounterRegistry {

    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();

    public void record(String ip) {
        String key = ip != null && !ip.isBlank() ? ip : "unknown";
        counts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        total.incrementAndGet();
    }

    public Map<String, Object> snapshot(int topN) {
        List<Map<String, Object>> top = counts.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicLong>comparingByValue(
                        Comparator.comparingLong(AtomicLong::get)).reversed())
                .limit(topN)
                .map(e -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("ip", e.getKey());
                    entry.put("requests", e.getValue().get());
                    return (Map<String, Object>) entry;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total.get());
        result.put("uniqueIps", counts.size());
        result.put("topIps", top);
        return Collections.unmodifiableMap(result);
    }

    public void reset() {
        counts.clear();
        total.set(0);
    }

    public long getTotal() {
        return total.get();
    }

    public int getUniqueIpCount() {
        return counts.size();
    }
}
