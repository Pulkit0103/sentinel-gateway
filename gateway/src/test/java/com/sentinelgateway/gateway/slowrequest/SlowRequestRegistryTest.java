package com.sentinelgateway.gateway.slowrequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SlowRequestRegistry} (Phase 33: Slow Request Detection).
 */
class SlowRequestRegistryTest {

    private SlowRequestRegistry registry;
    private SlowRequestProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SlowRequestProperties();
        properties.setMaxRecords(5);
        registry = new SlowRequestRegistry(properties);
    }

    private SlowRequestRecord rec(String path, long durationMs) {
        return new SlowRequestRecord(Instant.now(), "GET", path, durationMs, 200);
    }

    @Test
    void freshRegistry_isEmpty() {
        assertThat(registry.size()).isZero();
        assertThat(registry.all()).isEmpty();
    }

    @Test
    void record_addsEntry() {
        registry.record(rec("/api/slow", 3000));
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void all_returnsRecordsInOrder() {
        registry.record(rec("/api/a", 2500));
        registry.record(rec("/api/b", 3000));
        assertThat(registry.all()).extracting(SlowRequestRecord::path)
                .containsExactly("/api/a", "/api/b");
    }

    @Test
    void clear_removesAllEntries() {
        registry.record(rec("/api/slow", 2000));
        registry.clear();
        assertThat(registry.size()).isZero();
    }

    @Test
    void ringBuffer_evictsOldestWhenFull() {
        for (int i = 0; i < 5; i++) {
            registry.record(rec("/api/req-" + i, 2000 + i));
        }
        assertThat(registry.size()).isEqualTo(5);

        registry.record(rec("/api/overflow", 9999));
        assertThat(registry.size()).isEqualTo(5); // still at capacity
        // Newest should be overflow, oldest req-0 was evicted
        assertThat(registry.all().get(4).path()).isEqualTo("/api/overflow");
        assertThat(registry.all().get(0).path()).isEqualTo("/api/req-1");
    }

    @Test
    void record_capturesAllFields() {
        Instant before = Instant.now();
        registry.record(new SlowRequestRecord(Instant.now(), "POST", "/api/payment", 5432, 200));
        SlowRequestRecord r = registry.all().get(0);
        assertThat(r.method()).isEqualTo("POST");
        assertThat(r.path()).isEqualTo("/api/payment");
        assertThat(r.durationMs()).isEqualTo(5432);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.timestamp()).isAfterOrEqualTo(before);
    }
}
