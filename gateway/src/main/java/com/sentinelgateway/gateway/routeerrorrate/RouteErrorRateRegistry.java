package com.sentinelgateway.gateway.routeerrorrate;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Registry for per-route error response rate in sliding windows (Phase 65).
 *
 * Each route keeps two unbounded timestamp deques: one for all requests,
 * one for error (4xx + 5xx) responses. Snapshot computes counts in the last
 * 1 min, 5 min, and 15 min windows without any background thread.
 */
@Component
public class RouteErrorRateRegistry {

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> totalTs =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> errorTs =
            new ConcurrentHashMap<>();

    public void record(String route, boolean isError) {
        long now = System.currentTimeMillis();
        totalTs.computeIfAbsent(route, k -> new ConcurrentLinkedDeque<>()).addLast(now);
        if (isError) {
            errorTs.computeIfAbsent(route, k -> new ConcurrentLinkedDeque<>()).addLast(now);
        }
    }

    public Map<String, Object> snapshot() {
        long now = System.currentTimeMillis();
        long cut1m  = now - 60_000L;
        long cut5m  = now - 300_000L;
        long cut15m = now - 900_000L;

        Set<String> routes = new LinkedHashSet<>(totalTs.keySet());
        routes.addAll(errorTs.keySet());

        Map<String, Object> result = new LinkedHashMap<>();
        for (String route : routes) {
            Deque<Long> total = totalTs.getOrDefault(route, new ConcurrentLinkedDeque<>());
            Deque<Long> errors = errorTs.getOrDefault(route, new ConcurrentLinkedDeque<>());

            Map<String, Object> windows = new LinkedHashMap<>();
            for (long[] cut : new long[][]{{cut1m, 60}, {cut5m, 300}, {cut15m, 900}}) {
                long cutoff = cut[0];
                long seconds = cut[1];
                long totalCount = total.stream().filter(t -> t >= cutoff).count();
                long errorCount = errors.stream().filter(t -> t >= cutoff).count();
                double rate = totalCount > 0 ? (double) errorCount / totalCount * 100.0 : 0.0;
                windows.put(seconds + "s", Map.of(
                        "total", totalCount,
                        "errors", errorCount,
                        "errorRatePct", Math.round(rate * 10.0) / 10.0
                ));
            }
            result.put(route, windows);
        }
        return result;
    }

    public void reset() {
        totalTs.clear();
        errorTs.clear();
    }

    public int routeCount() { return totalTs.size(); }
}
