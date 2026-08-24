package com.sentinelgateway.gateway.errorrate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ErrorRateRegistry} (Phase 35: Error Rate Tracking).
 */
class ErrorRateRegistryTest {

    private ErrorRateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ErrorRateRegistry();
    }

    @Test
    void freshRegistry_isEmpty() {
        assertThat(registry.routeCount()).isZero();
        assertThat(registry.snapshots()).isEmpty();
    }

    @Test
    void record_200_onlyIncrementsTotalNotErrors() {
        registry.record("/api/users", 200);
        Map<String, Object> snap = registry.snapshots().get("/api/users");
        assertThat(((Number) snap.get("total")).longValue()).isEqualTo(1);
        assertThat(((Number) snap.get("errors")).longValue()).isZero();
        assertThat(((Number) snap.get("errorRatePct")).doubleValue()).isEqualTo(0.0);
    }

    @Test
    void record_500_incrementsBothTotalAndErrors() {
        registry.record("/api/orders", 500);
        Map<String, Object> snap = registry.snapshots().get("/api/orders");
        assertThat(((Number) snap.get("total")).longValue()).isEqualTo(1);
        assertThat(((Number) snap.get("errors")).longValue()).isEqualTo(1);
        assertThat(((Number) snap.get("errorRatePct")).doubleValue()).isEqualTo(100.0);
    }

    @Test
    void record_400_countsAsError() {
        registry.record("/api/payments", 400);
        Map<String, Object> snap = registry.snapshots().get("/api/payments");
        assertThat(((Number) snap.get("errors")).longValue()).isEqualTo(1);
    }

    @Test
    void errorRatePct_halfErrors_is50Percent() {
        registry.record("/api/test", 200);
        registry.record("/api/test", 500);
        Map<String, Object> snap = registry.snapshots().get("/api/test");
        assertThat(((Number) snap.get("errorRatePct")).doubleValue()).isEqualTo(50.0);
    }

    @Test
    void multipleRoutes_trackedIndependently() {
        registry.record("/api/users", 200);
        registry.record("/api/orders", 500);
        assertThat(registry.routeCount()).isEqualTo(2);
        assertThat(registry.snapshots()).containsKey("/api/users");
        assertThat(registry.snapshots()).containsKey("/api/orders");
    }

    @Test
    void reset_clearsAllCounters() {
        registry.record("/api/users", 200);
        registry.record("/api/orders", 500);
        registry.reset();
        assertThat(registry.routeCount()).isZero();
        assertThat(registry.snapshots()).isEmpty();
    }

    @Test
    void pathBucket_groupsToTwoSegments() {
        assertThat(ErrorRateFilter.pathBucket("/api/users/123")).isEqualTo("/api/users");
        assertThat(ErrorRateFilter.pathBucket("/api/users")).isEqualTo("/api/users");
        assertThat(ErrorRateFilter.pathBucket("/api")).isEqualTo("/api");
    }
}
