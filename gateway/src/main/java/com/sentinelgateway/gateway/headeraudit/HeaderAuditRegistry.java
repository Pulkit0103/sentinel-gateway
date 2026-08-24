package com.sentinelgateway.gateway.headeraudit;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks presence/absence counts for each audited response header (Phase 51).
 *
 * For each tracked header: counts how many responses included it (present) vs omitted it (absent).
 * Computes an overall coverage percentage across all responses × all tracked headers.
 */
@Component
public class HeaderAuditRegistry {

    // header → {present, absent}
    private final ConcurrentHashMap<String, AtomicLong[]> counts = new ConcurrentHashMap<>();
    private final AtomicLong totalResponses = new AtomicLong();

    private static final int PRESENT = 0;
    private static final int ABSENT  = 1;

    public void record(String header, boolean present) {
        AtomicLong[] pair = counts.computeIfAbsent(header,
                k -> new AtomicLong[]{new AtomicLong(), new AtomicLong()});
        pair[present ? PRESENT : ABSENT].incrementAndGet();
    }

    public void incrementResponses() {
        totalResponses.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<String, Map<String, Long>> perHeader = new LinkedHashMap<>();
        long totalPresent = 0;
        long totalSlots = 0;

        List<String> keys = new ArrayList<>(counts.keySet());
        Collections.sort(keys);
        for (String header : keys) {
            AtomicLong[] pair = counts.get(header);
            long present = pair[PRESENT].get();
            long absent  = pair[ABSENT].get();
            Map<String, Long> entry = new LinkedHashMap<>();
            entry.put("present", present);
            entry.put("absent", absent);
            perHeader.put(header, entry);
            totalPresent += present;
            totalSlots   += present + absent;
        }

        double coveragePct = totalSlots > 0 ? (100.0 * totalPresent / totalSlots) : 100.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalResponses", totalResponses.get());
        result.put("coveragePercent", Math.round(coveragePct * 10.0) / 10.0);
        result.put("headers", perHeader);
        return Collections.unmodifiableMap(result);
    }

    public void reset() {
        counts.clear();
        totalResponses.set(0);
    }

    public long getTotalResponses() {
        return totalResponses.get();
    }
}
