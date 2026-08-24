package com.sentinelgateway.gateway.responsesize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResponseSizeRegistry} (Phase 42: Response Body Size Tracking).
 */
class ResponseSizeRegistryTest {

    private ResponseSizeRegistry registry;

    @BeforeEach
    void setUp() {
        ResponseSizeProperties props = new ResponseSizeProperties();
        props.setMaxRecords(5);
        registry = new ResponseSizeRegistry(props);
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void emptyRegistry_allZero() {
        assertThat(registry.getSampleCount()).isZero();
        assertThat(registry.getTotalBytes()).isZero();
        assertThat(registry.getAverageBytes()).isZero();
        assertThat(registry.recentSamples()).isEmpty();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void recordSingle_statsCorrect() {
        registry.record(1000L);
        assertThat(registry.getSampleCount()).isEqualTo(1L);
        assertThat(registry.getTotalBytes()).isEqualTo(1000L);
        assertThat(registry.getAverageBytes()).isEqualTo(1000L);
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void recordMultiple_averageCorrect() {
        registry.record(100L);
        registry.record(200L);
        registry.record(300L);
        assertThat(registry.getSampleCount()).isEqualTo(3L);
        assertThat(registry.getTotalBytes()).isEqualTo(600L);
        assertThat(registry.getAverageBytes()).isEqualTo(200L);
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void ringBuffer_evictsOldestSamples() {
        for (int i = 1; i <= 6; i++) {
            registry.record(i * 100L);
        }
        // ring buffer capacity = 5; first sample evicted from samples list
        assertThat(registry.recentSamples()).hasSize(5);
        assertThat(registry.recentSamples()).doesNotContain(100L);
        assertThat(registry.recentSamples()).contains(600L);
        // totalBytes and sampleCount still count all 6
        assertThat(registry.getSampleCount()).isEqualTo(6L);
        assertThat(registry.getTotalBytes()).isEqualTo(2100L);
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void reset_clearsAllState() {
        registry.record(500L);
        registry.reset();
        assertThat(registry.getSampleCount()).isZero();
        assertThat(registry.getTotalBytes()).isZero();
        assertThat(registry.recentSamples()).isEmpty();
    }
}
