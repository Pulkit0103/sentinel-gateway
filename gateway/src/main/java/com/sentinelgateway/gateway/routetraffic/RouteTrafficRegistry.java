package com.sentinelgateway.gateway.routetraffic;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-route windowed request counter (Phase 53).
 *
 * Each request is timestamped and stored in a per-route deque.
 * Snapshot queries compute counts within 1-min, 5-min, 15-min windows
 * by scanning timestamps without any background threads.
 */
@Component
public class RouteTrafficRegistry {

    private static final int MAX_ENTRIES_PER_ROUTE = 10000;

    private final ConcurrentHashMap<String, LinkedBlockingDeque<Long>> routeTimestamps
            = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> routeTotals = new ConcurrentHashMap<>();

    public void record(String route) {
        long now = Instant.now().toEpochMilli();
        LinkedBlockingDeque<Long> deque = routeTimestamps.computeIfAbsent(
                route, k -> new LinkedBlockingDeque<>(MAX_ENTRIES_PER_ROUTE));
        if (deque.size() >= MAX_ENTRIES_PER_ROUTE) {
            deque.pollFirst();
        }
        deque.offerLast(now);
        routeTotals.computeIfAbsent(route, k -> new AtomicLong()).incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        long now = Instant.now().toEpochMilli();
        long cutoff1m  = now - 60_000L;
        long cutoff5m  = now - 300_000L;
        long cutoff15m = now - 900_000L;

        Map<String, Map<String, Object>> routes = new LinkedHashMap<>();
        List<String> sortedRoutes = new ArrayList<>(routeTimestamps.keySet());
        Collections.sort(sortedRoutes);

        for (String route : sortedRoutes) {
            LinkedBlockingDeque<Long> deque = routeTimestamps.get(route);
            if (deque == null) continue;
            List<Long> ts = new ArrayList<>(deque);

            long count1m  = ts.stream().filter(t -> t >= cutoff1m).count();
            long count5m  = ts.stream().filter(t -> t >= cutoff5m).count();
            long count15m = ts.stream().filter(t -> t >= cutoff15m).count();
            long total    = routeTotals.getOrDefault(route, new AtomicLong()).get();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("last1m", count1m);
            entry.put("last5m", count5m);
            entry.put("last15m", count15m);
            entry.put("total", total);
            routes.put(route, entry);
        }

        return Map.of("routes", Collections.unmodifiableMap(routes));
    }

    public void reset() {
        routeTimestamps.clear();
        routeTotals.clear();
    }
}
