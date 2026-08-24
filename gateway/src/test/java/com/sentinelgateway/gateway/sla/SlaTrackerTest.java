package com.sentinelgateway.gateway.sla;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SlaTracker} (Phase 25: Response Time SLA Tracking).
 */
class SlaTrackerTest {

    private SlaTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new SlaTracker();
    }

    @Test
    void freshTracker_noSnapshots() {
        assertThat(tracker.snapshots()).isEmpty();
    }

    @Test
    void record_withNoBreach_countsBut0Breaches() {
        tracker.record("my-route", 100, 500); // 100ms < 500ms target
        SlaTracker.SlaSnapshot snap = tracker.snapshots().get("my-route");
        assertThat(snap).isNotNull();
        assertThat(snap.totalRequests()).isEqualTo(1);
        assertThat(snap.breachCount()).isZero();
    }

    @Test
    void record_withBreach_incrementsBreachCount() {
        tracker.record("my-route", 600, 500); // 600ms > 500ms target
        SlaTracker.SlaSnapshot snap = tracker.snapshots().get("my-route");
        assertThat(snap.breachCount()).isEqualTo(1);
    }

    @Test
    void record_multipleRequests_aggregatesCorrectly() {
        tracker.record("svc", 100, 500);
        tracker.record("svc", 700, 500); // breach
        tracker.record("svc", 200, 500);

        SlaTracker.SlaSnapshot snap = tracker.snapshots().get("svc");
        assertThat(snap.totalRequests()).isEqualTo(3);
        assertThat(snap.breachCount()).isEqualTo(1);
        assertThat(snap.meanDurationMs()).isEqualTo((100 + 700 + 200) / 3); // 333
        assertThat(snap.maxDurationMs()).isEqualTo(700);
    }

    @Test
    void record_breachRatePct_calculatesCorrectly() {
        tracker.record("route", 600, 500); // breach
        tracker.record("route", 100, 500); // no breach

        SlaTracker.SlaSnapshot snap = tracker.snapshots().get("route");
        assertThat(snap.breachRatePct()).isEqualTo(50.0);
    }

    @Test
    void reset_clearsAllStats() {
        tracker.record("route-a", 100, 500);
        tracker.reset();
        assertThat(tracker.snapshots()).isEmpty();
    }

    @Test
    void multipleRoutes_trackedIndependently() {
        tracker.record("route-a", 100, 500);
        tracker.record("route-b", 600, 500);

        assertThat(tracker.snapshots().get("route-a").breachCount()).isZero();
        assertThat(tracker.snapshots().get("route-b").breachCount()).isEqualTo(1);
    }
}
