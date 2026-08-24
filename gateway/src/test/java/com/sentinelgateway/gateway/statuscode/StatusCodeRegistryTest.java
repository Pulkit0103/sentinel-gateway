package com.sentinelgateway.gateway.statuscode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StatusCodeRegistry} (Phase 40: Status Code Distribution).
 */
class StatusCodeRegistryTest {

    private StatusCodeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new StatusCodeRegistry();
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void emptyRegistry_totalIsZero() {
        Map<String, Object> snap = registry.snapshot();
        assertThat(((Number) snap.get("total")).longValue()).isZero();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void record200_appears2xxBucket() {
        registry.record(200);
        registry.record(201);

        Map<String, Object> snap = registry.snapshot();
        assertThat(((Number) snap.get("total")).longValue()).isEqualTo(2L);
        Map<String, Object> buckets = (Map<String, Object>) snap.get("buckets");
        assertThat(((Number) buckets.get("2xx")).longValue()).isEqualTo(2L);
        assertThat(((Number) buckets.get("4xx")).longValue()).isZero();
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void record400And500_segregatedIntoBuckets() {
        registry.record(400);
        registry.record(404);
        registry.record(500);
        registry.record(503);

        Map<String, Object> snap = registry.snapshot();
        Map<String, Object> buckets = (Map<String, Object>) snap.get("buckets");
        assertThat(((Number) buckets.get("4xx")).longValue()).isEqualTo(2L);
        assertThat(((Number) buckets.get("5xx")).longValue()).isEqualTo(2L);
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void perCodeCounts_areAccurate() {
        registry.record(200);
        registry.record(200);
        registry.record(404);

        Map<String, Object> snap = registry.snapshot();
        Map<Integer, Long> codes = (Map<Integer, Long>) snap.get("codes");
        assertThat(codes.get(200)).isEqualTo(2L);
        assertThat(codes.get(404)).isEqualTo(1L);
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void reset_clearsAllCounters() {
        registry.record(200);
        registry.record(500);
        registry.reset();

        Map<String, Object> snap = registry.snapshot();
        assertThat(((Number) snap.get("total")).longValue()).isZero();
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void record301_appears3xxBucket() {
        registry.record(301);
        registry.record(302);

        Map<String, Object> snap = registry.snapshot();
        Map<String, Object> buckets = (Map<String, Object>) snap.get("buckets");
        assertThat(((Number) buckets.get("3xx")).longValue()).isEqualTo(2L);
    }
}
