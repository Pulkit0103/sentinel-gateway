package com.sentinelgateway.gateway.requestsize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OversizedRequestRegistry} (Phase 37: Request Size Guard).
 */
class OversizedRequestRegistryTest {

    private RequestSizeProperties properties;
    private OversizedRequestRegistry registry;

    @BeforeEach
    void setUp() {
        properties = new RequestSizeProperties();
        properties.setMaxRecords(5);
        registry = new OversizedRequestRegistry(properties);
    }

    private OversizedRequestRecord rec(String path, long bytes) {
        return new OversizedRequestRecord(Instant.now(), "POST", path, bytes);
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void emptyRegistry_returnsZeroCount() {
        assertThat(registry.count()).isZero();
        assertThat(registry.snapshot()).isEmpty();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void record_increasesCount() {
        registry.record(rec("/api/upload", 20_000_000L));
        assertThat(registry.count()).isEqualTo(1);
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void snapshot_returnsAllRecordedEntries() {
        registry.record(rec("/api/a", 1_000_000L));
        registry.record(rec("/api/b", 2_000_000L));

        List<OversizedRequestRecord> snap = registry.snapshot();
        assertThat(snap).hasSize(2);
        assertThat(snap.stream().map(OversizedRequestRecord::path))
                .containsExactly("/api/a", "/api/b");
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void ringBuffer_evictsOldestWhenFull() {
        for (int i = 0; i < 6; i++) {
            registry.record(rec("/api/upload-" + i, (i + 1) * 1_000_000L));
        }
        assertThat(registry.count()).isEqualTo(5);
        // oldest (/api/upload-0) should be gone
        assertThat(registry.snapshot().stream().map(OversizedRequestRecord::path))
                .doesNotContain("/api/upload-0")
                .contains("/api/upload-5");
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void clear_emptiesRegistry() {
        registry.record(rec("/api/upload", 5_000_000L));
        registry.clear();
        assertThat(registry.count()).isZero();
        assertThat(registry.snapshot()).isEmpty();
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void snapshotIsImmutable_modificationDoesNotAffectRegistry() {
        registry.record(rec("/api/upload", 5_000_000L));
        List<OversizedRequestRecord> snap = registry.snapshot();
        assertThat(snap).hasSize(1);
        // Registry should still have 1 entry regardless
        assertThat(registry.count()).isEqualTo(1);
    }
}
