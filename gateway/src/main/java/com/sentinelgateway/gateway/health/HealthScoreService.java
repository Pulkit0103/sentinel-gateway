package com.sentinelgateway.gateway.health;

import com.sentinelgateway.gateway.errorrate.ErrorRateRegistry;
import com.sentinelgateway.gateway.slowrequest.SlowRequestRegistry;
import com.sentinelgateway.gateway.uptime.UptimeRegistry;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Computes a composite 0-100 health score from operational signals (Phase 39).
 *
 * Factors (each 0-100, combined as a simple average):
 *   - errorRate:    100 − clamp(overallErrorRatePct × 5, 0, 100)
 *                   e.g. 0% errors → 100, 20%+ errors → 0
 *   - slowRequests: 100 − clamp(slowRequestCount × 2, 0, 100)
 *                   e.g. 0 slow requests → 100, 50+ → 0
 *   - uptime:       100 if uptimeSec ≥ 60, else uptimeSec × 100/60
 *
 * Grade: A (≥90), B (≥75), C (≥60), D (≥40), F (<40).
 */
@Service
public class HealthScoreService {

    private final ErrorRateRegistry errorRateRegistry;
    private final SlowRequestRegistry slowRequestRegistry;
    private final UptimeRegistry uptimeRegistry;

    public HealthScoreService(ErrorRateRegistry errorRateRegistry,
                              SlowRequestRegistry slowRequestRegistry,
                              UptimeRegistry uptimeRegistry) {
        this.errorRateRegistry = errorRateRegistry;
        this.slowRequestRegistry = slowRequestRegistry;
        this.uptimeRegistry = uptimeRegistry;
    }

    public HealthScoreResult compute() {
        double errorFactor = computeErrorFactor();
        double slowFactor = computeSlowFactor();
        double uptimeFactor = computeUptimeFactor();

        int score = (int) Math.round((errorFactor + slowFactor + uptimeFactor) / 3.0);
        score = Math.max(0, Math.min(100, score));

        Map<String, Integer> factors = new LinkedHashMap<>();
        factors.put("errorRate", (int) Math.round(errorFactor));
        factors.put("slowRequests", (int) Math.round(slowFactor));
        factors.put("uptime", (int) Math.round(uptimeFactor));

        return new HealthScoreResult(score, grade(score), factors);
    }

    private double computeErrorFactor() {
        Map<String, Map<String, Object>> snaps = errorRateRegistry.snapshots();
        if (snaps.isEmpty()) return 100.0;
        long totalRequests = snaps.values().stream()
                .mapToLong(s -> ((Number) s.get("total")).longValue()).sum();
        long totalErrors = snaps.values().stream()
                .mapToLong(s -> ((Number) s.get("errors")).longValue()).sum();
        if (totalRequests == 0) return 100.0;
        double errorPct = (totalErrors * 100.0) / totalRequests;
        return Math.max(0, 100 - errorPct * 5);
    }

    private double computeSlowFactor() {
        int slowCount = slowRequestRegistry.size();
        return Math.max(0, 100 - slowCount * 2.0);
    }

    private double computeUptimeFactor() {
        long uptimeSec = uptimeRegistry.uptimeSeconds();
        if (uptimeSec >= 60) return 100.0;
        return uptimeSec * 100.0 / 60.0;
    }

    static String grade(int score) {
        if (score >= 90) return "A";
        if (score >= 75) return "B";
        if (score >= 60) return "C";
        if (score >= 40) return "D";
        return "F";
    }
}
