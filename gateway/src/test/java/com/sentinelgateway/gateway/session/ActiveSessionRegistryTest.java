package com.sentinelgateway.gateway.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ActiveSessionRegistry} (Phase 27: Active Session Tracking).
 */
class ActiveSessionRegistryTest {

    private ActiveSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ActiveSessionRegistry();
    }

    @Test
    void freshRegistry_isEmpty() {
        assertThat(registry.totalTracked()).isZero();
        assertThat(registry.activeSince(Instant.EPOCH)).isEmpty();
    }

    @Test
    void recordActivity_tracksSubject() {
        registry.recordActivity("user-123");
        assertThat(registry.totalTracked()).isEqualTo(1);
    }

    @Test
    void recordActivity_nullSubject_isIgnored() {
        registry.recordActivity(null);
        assertThat(registry.totalTracked()).isZero();
    }

    @Test
    void recordActivity_blankSubject_isIgnored() {
        registry.recordActivity("  ");
        assertThat(registry.totalTracked()).isZero();
    }

    @Test
    void activeSince_recentActivity_returnsSubject() {
        registry.recordActivity("user-abc");
        assertThat(registry.activeSince(Instant.now().minusSeconds(60))).contains("user-abc");
    }

    @Test
    void activeSince_futureInstant_returnsEmpty() {
        registry.recordActivity("user-abc");
        assertThat(registry.activeSince(Instant.now().plusSeconds(60))).isEmpty();
    }

    @Test
    void evictBefore_removesOldEntries() {
        registry.recordActivity("old-user");
        // Evict everything older than 1 second in the future (i.e., evict all)
        registry.evictBefore(Instant.now().plusSeconds(1));
        assertThat(registry.totalTracked()).isZero();
    }

    @Test
    void evictBefore_keepsRecentEntries() {
        registry.recordActivity("recent-user");
        // Evict only entries older than a time far in the past (evicts nothing)
        registry.evictBefore(Instant.EPOCH);
        assertThat(registry.totalTracked()).isEqualTo(1);
    }

    @Test
    void clear_removesAll() {
        registry.recordActivity("a");
        registry.recordActivity("b");
        registry.clear();
        assertThat(registry.totalTracked()).isZero();
    }

    @Test
    void multipleSubjects_trackedIndependently() {
        registry.recordActivity("user-1");
        registry.recordActivity("user-2");
        registry.recordActivity("user-1"); // update last-seen
        assertThat(registry.totalTracked()).isEqualTo(2);
        assertThat(registry.activeSince(Instant.EPOCH)).containsExactlyInAnyOrder("user-1", "user-2");
    }
}
