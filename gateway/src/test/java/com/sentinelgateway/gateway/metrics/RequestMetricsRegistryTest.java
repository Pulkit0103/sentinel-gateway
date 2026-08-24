package com.sentinelgateway.gateway.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RequestMetricsRegistry} (Phase 23: Active Request Metrics).
 */
class RequestMetricsRegistryTest {

    private RequestMetricsRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RequestMetricsRegistry();
    }

    @Test
    void freshRegistry_allCountersAreZero() {
        assertThat(registry.getInflightRequests()).isZero();
        assertThat(registry.getTotalCompleted()).isZero();
        assertThat(registry.getPerRouteCompleted()).isEmpty();
    }

    @Test
    void requestStarted_incrementsInflight() {
        registry.requestStarted();
        assertThat(registry.getInflightRequests()).isEqualTo(1);
    }

    @Test
    void requestCompleted_decrementsInflight() {
        registry.requestStarted();
        registry.requestCompleted("my-route");
        assertThat(registry.getInflightRequests()).isZero();
    }

    @Test
    void requestCompleted_incrementsTotalCompleted() {
        registry.requestStarted();
        registry.requestCompleted("my-route");
        assertThat(registry.getTotalCompleted()).isEqualTo(1);
    }

    @Test
    void requestCompleted_withRouteId_incrementsPerRoute() {
        registry.requestStarted();
        registry.requestCompleted("payment-service");
        assertThat(registry.getPerRouteCompleted()).containsEntry("payment-service", 1L);
    }

    @Test
    void requestCompleted_nullRouteId_onlyIncrementsTotalCompleted() {
        registry.requestStarted();
        registry.requestCompleted(null);
        assertThat(registry.getTotalCompleted()).isEqualTo(1);
        assertThat(registry.getPerRouteCompleted()).isEmpty();
    }

    @Test
    void multipleRequests_tracked_correctly() {
        registry.requestStarted();
        registry.requestStarted();
        registry.requestStarted();
        registry.requestCompleted("route-a");
        registry.requestCompleted("route-a");

        assertThat(registry.getInflightRequests()).isEqualTo(1); // 3 started - 2 completed
        assertThat(registry.getTotalCompleted()).isEqualTo(2);
        assertThat(registry.getPerRouteCompleted().get("route-a")).isEqualTo(2L);
    }

    @Test
    void reset_clearsAllCounters() {
        registry.requestStarted();
        registry.requestCompleted("some-route");
        registry.reset();

        assertThat(registry.getInflightRequests()).isZero();
        assertThat(registry.getTotalCompleted()).isZero();
        assertThat(registry.getPerRouteCompleted()).isEmpty();
    }
}
