package com.sentinelgateway.gateway.latency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LatencyRegistry} and {@link LatencySnapshot} (Phase 38).
 */
class LatencyRegistryTest {

    private LatencyProperties properties;
    private LatencyRegistry registry;

    @BeforeEach
    void setUp() {
        properties = new LatencyProperties();
        properties.setMaxSamplesPerRoute(10);
        registry = new LatencyRegistry(properties);
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void emptyRegistry_returnsEmptySnapshot() {
        assertThat(registry.routeCount()).isZero();
        assertThat(registry.snapshots()).isEmpty();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void singleSample_allPercentilesEqual() {
        registry.record("/api/users", 100L);

        LatencySnapshot snap = registry.snapshots().get("/api/users");
        assertThat(snap).isNotNull();
        assertThat(snap.p50Ms()).isEqualTo(100L);
        assertThat(snap.p95Ms()).isEqualTo(100L);
        assertThat(snap.p99Ms()).isEqualTo(100L);
        assertThat(snap.sampleCount()).isEqualTo(1);
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void multiSample_percentilesCorrect() {
        // 10 samples: 10, 20, 30, ... 100
        for (int i = 1; i <= 10; i++) {
            registry.record("/api/orders", i * 10L);
        }

        LatencySnapshot snap = registry.snapshots().get("/api/orders");
        assertThat(snap.sampleCount()).isEqualTo(10);
        assertThat(snap.p50Ms()).isEqualTo(50L);  // median of [10..100]
        assertThat(snap.p95Ms()).isEqualTo(100L); // 95th of 10 items → last
        assertThat(snap.p99Ms()).isEqualTo(100L);
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void ringBuffer_evictsOldestSamplesWhenFull() {
        // Fill to capacity (10) then add one more
        for (int i = 1; i <= 10; i++) {
            registry.record("/api/pay", 1L); // old values
        }
        registry.record("/api/pay", 9999L); // should evict first 1

        LatencySnapshot snap = registry.snapshots().get("/api/pay");
        // Still exactly 10 samples, but p99 should now be 9999
        assertThat(snap.sampleCount()).isEqualTo(10);
        assertThat(snap.p99Ms()).isEqualTo(9999L);
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void multipleRoutes_trackedIndependently() {
        registry.record("/api/users", 100L);
        registry.record("/api/orders", 200L);

        Map<String, LatencySnapshot> snaps = registry.snapshots();
        assertThat(snaps).containsKeys("/api/users", "/api/orders");
        assertThat(snaps.get("/api/users").p50Ms()).isEqualTo(100L);
        assertThat(snaps.get("/api/orders").p50Ms()).isEqualTo(200L);
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void reset_clearsAllRoutes() {
        registry.record("/api/users", 100L);
        registry.record("/api/orders", 200L);

        registry.reset();

        assertThat(registry.routeCount()).isZero();
        assertThat(registry.snapshots()).isEmpty();
    }

    // ── Test 7 ──────────────────────────────────────────────────────────────────

    @Test
    void pathBucket_groupsSubPaths() {
        assertThat(LatencyFilter.pathBucket("/api/users/123")).isEqualTo("/api/users");
        assertThat(LatencyFilter.pathBucket("/api/orders/456/items")).isEqualTo("/api/orders");
        assertThat(LatencyFilter.pathBucket("/api")).isEqualTo("/api");
        assertThat(LatencyFilter.pathBucket("/")).isEqualTo("/");
    }
}
