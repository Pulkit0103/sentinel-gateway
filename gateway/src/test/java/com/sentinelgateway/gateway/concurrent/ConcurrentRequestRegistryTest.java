package com.sentinelgateway.gateway.concurrent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConcurrentRequestRegistry} (Phase 41: Concurrent Request Monitor).
 */
class ConcurrentRequestRegistryTest {

    private ConcurrentRequestRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ConcurrentRequestRegistry();
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void initialState_allZero() {
        assertThat(registry.getCurrent()).isZero();
        assertThat(registry.getPeak()).isZero();
        assertThat(registry.getTotalCompleted()).isZero();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void increment_increasesCurrent() {
        registry.increment();
        assertThat(registry.getCurrent()).isEqualTo(1);
        assertThat(registry.getPeak()).isEqualTo(1);
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void decrement_decreasesCurrent_incrementsCompleted() {
        registry.increment();
        registry.decrement();
        assertThat(registry.getCurrent()).isZero();
        assertThat(registry.getTotalCompleted()).isEqualTo(1L);
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void peak_tracksHighWaterMark() {
        registry.increment();
        registry.increment();
        registry.increment();
        registry.decrement();
        // current=2, peak should still be 3
        assertThat(registry.getPeak()).isEqualTo(3);
        assertThat(registry.getCurrent()).isEqualTo(2);
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void resetPeak_setsPeakToCurrent() {
        registry.increment();
        registry.increment();
        registry.increment();
        registry.decrement(); // current=2, peak=3

        registry.resetPeak();
        assertThat(registry.getPeak()).isEqualTo(registry.getCurrent());
        assertThat(registry.getTotalCompleted()).isZero();
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void concurrentIncrements_peakNeverUnderCounts() throws InterruptedException {
        int threads = 10;
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> registry.increment());
        }
        for (Thread t : workers) t.start();
        for (Thread t : workers) t.join();

        assertThat(registry.getPeak()).isGreaterThanOrEqualTo(registry.getCurrent());
        assertThat(registry.getCurrent()).isEqualTo(threads);
    }
}
