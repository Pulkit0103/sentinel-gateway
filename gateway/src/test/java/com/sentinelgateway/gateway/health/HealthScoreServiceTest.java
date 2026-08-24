package com.sentinelgateway.gateway.health;

import com.sentinelgateway.gateway.errorrate.ErrorRateRegistry;
import com.sentinelgateway.gateway.slowrequest.SlowRequestProperties;
import com.sentinelgateway.gateway.slowrequest.SlowRequestRecord;
import com.sentinelgateway.gateway.slowrequest.SlowRequestRegistry;
import com.sentinelgateway.gateway.uptime.UptimeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HealthScoreService} (Phase 39: Gateway Health Score).
 */
class HealthScoreServiceTest {

    private ErrorRateRegistry errorRateRegistry;
    private SlowRequestRegistry slowRequestRegistry;
    private UptimeRegistry uptimeRegistry;
    private HealthScoreService service;

    @BeforeEach
    void setUp() {
        errorRateRegistry = new ErrorRateRegistry();
        SlowRequestProperties slowProps = new SlowRequestProperties();
        slowProps.setMaxRecords(50);
        slowRequestRegistry = new SlowRequestRegistry(slowProps);
        uptimeRegistry = new UptimeRegistry();
        service = new HealthScoreService(errorRateRegistry, slowRequestRegistry, uptimeRegistry);
    }

    private SlowRequestRecord slowRec() {
        return new SlowRequestRecord(Instant.now(), "GET", "/api/test", 5000L, 200);
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void noSignals_scoreIsHigh() {
        HealthScoreResult result = service.compute();
        // uptime factor will be lower (fresh start), but error/slow factors are 100
        assertThat(result.score()).isGreaterThanOrEqualTo(60);
        assertThat(result.grade()).isNotNull();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void highErrorRate_reducesScore() {
        // 100% error rate → error factor = 0
        for (int i = 0; i < 10; i++) {
            errorRateRegistry.record("/api/test", 500);
        }

        HealthScoreResult result = service.compute();
        assertThat(result.factors().get("errorRate")).isEqualTo(0);
        assertThat(result.score()).isLessThan(70);
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void noErrors_errorFactorIs100() {
        for (int i = 0; i < 10; i++) {
            errorRateRegistry.record("/api/test", 200);
        }

        HealthScoreResult result = service.compute();
        assertThat(result.factors().get("errorRate")).isEqualTo(100);
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void manySlowRequests_reducesSlowFactor() {
        // 50 slow requests → slow factor = 0
        for (int i = 0; i < 50; i++) {
            slowRequestRegistry.record(slowRec());
        }

        HealthScoreResult result = service.compute();
        assertThat(result.factors().get("slowRequests")).isEqualTo(0);
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void gradeMapping_correct() {
        // All factors at 100 → score ~100 (uptime <60s gives partial, but check grade boundary)
        assertThat(HealthScoreService.grade(95)).isEqualTo("A");
        assertThat(HealthScoreService.grade(80)).isEqualTo("B");
        assertThat(HealthScoreService.grade(65)).isEqualTo("C");
        assertThat(HealthScoreService.grade(45)).isEqualTo("D");
        assertThat(HealthScoreService.grade(30)).isEqualTo("F");
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void result_containsAllFactors() {
        HealthScoreResult result = service.compute();
        assertThat(result.factors()).containsKeys("errorRate", "slowRequests", "uptime");
        assertThat(result.score()).isBetween(0, 100);
    }
}
