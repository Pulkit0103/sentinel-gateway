package com.sentinelgateway.gateway.uptime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UptimeRegistry} (Phase 30: Gateway Uptime Tracking).
 */
class UptimeRegistryTest {

    private UptimeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new UptimeRegistry();
    }

    @Test
    void freshRegistry_startTimeIsRecent() {
        assertThat(registry.getStartTime()).isBetween(
                Instant.now().minusSeconds(5), Instant.now().plusSeconds(1));
    }

    @Test
    void freshRegistry_requestCountIsZero() {
        assertThat(registry.getRequestCount()).isZero();
    }

    @Test
    void recordRequest_incrementsCount() {
        registry.recordRequest();
        registry.recordRequest();
        assertThat(registry.getRequestCount()).isEqualTo(2);
    }

    @Test
    void uptimeSeconds_isNonNegative() {
        assertThat(registry.uptimeSeconds()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void reset_clearsRequestCount() {
        registry.recordRequest();
        registry.recordRequest();
        registry.reset();
        assertThat(registry.getRequestCount()).isZero();
    }

    @Test
    void reset_doesNotAffectStartTime() {
        Instant before = registry.getStartTime();
        registry.reset();
        assertThat(registry.getStartTime()).isEqualTo(before);
    }

    @Test
    void concurrentRecordRequest_threadsafe() throws InterruptedException {
        int threads = 10;
        int perThread = 100;
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) registry.recordRequest();
            });
        }
        for (Thread t : workers) t.start();
        for (Thread t : workers) t.join();
        assertThat(registry.getRequestCount()).isEqualTo((long) threads * perThread);
    }
}
