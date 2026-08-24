package com.sentinelgateway.gateway.errorrate;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory error rate tracker per route (Phase 35).
 *
 * Records total requests and 4xx/5xx counts per route path prefix.
 * Thread-safe via AtomicLong. State is ephemeral.
 */
@Component
public class ErrorRateRegistry {

    private static final class RouteStats {
        final AtomicLong total = new AtomicLong();
        final AtomicLong errors = new AtomicLong();
    }

    private final ConcurrentHashMap<String, RouteStats> stats = new ConcurrentHashMap<>();

    public void record(String route, int statusCode) {
        RouteStats s = stats.computeIfAbsent(route, k -> new RouteStats());
        s.total.incrementAndGet();
        if (statusCode >= 400) {
            s.errors.incrementAndGet();
        }
    }

    /**
     * Returns a snapshot: routeId → {total, errors, errorRatePct}.
     */
    public Map<String, Map<String, Object>> snapshots() {
        return stats.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> {
                    long total = e.getValue().total.get();
                    long errors = e.getValue().errors.get();
                    double ratePct = total > 0 ? (errors * 100.0 / total) : 0.0;
                    return Map.of(
                            "total", total,
                            "errors", errors,
                            "errorRatePct", Math.round(ratePct * 100.0) / 100.0
                    );
                }
        ));
    }

    public int routeCount() {
        return stats.size();
    }

    public void reset() {
        stats.clear();
    }
}
