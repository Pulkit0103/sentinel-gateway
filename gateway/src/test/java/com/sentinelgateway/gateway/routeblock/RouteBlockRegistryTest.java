package com.sentinelgateway.gateway.routeblock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RouteBlockRegistry} (Phase 20: Admin Route Blocking).
 */
class RouteBlockRegistryTest {

    private RouteBlockRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RouteBlockRegistry();
    }

    @Test
    void freshRegistry_hasNoBlockedRoutes() {
        assertThat(registry.blockedRouteIds()).isEmpty();
    }

    @Test
    void block_routeIsBlocked() {
        registry.block("payment-service");
        assertThat(registry.isBlocked("payment-service")).isTrue();
    }

    @Test
    void isBlocked_unknownRoute_returnsFalse() {
        assertThat(registry.isBlocked("unknown-route")).isFalse();
    }

    @Test
    void isBlocked_nullRouteId_returnsFalse() {
        assertThat(registry.isBlocked(null)).isFalse();
    }

    @Test
    void unblock_removesBlock() {
        registry.block("order-service");
        registry.unblock("order-service");
        assertThat(registry.isBlocked("order-service")).isFalse();
    }

    @Test
    void unblock_idempotent_doesNotThrow() {
        // unblocking a route that was never blocked should be a no-op
        registry.unblock("never-blocked-route");
        assertThat(registry.isBlocked("never-blocked-route")).isFalse();
    }

    @Test
    void unblockAll_clearsAllBlocks() {
        registry.block("route-a");
        registry.block("route-b");
        registry.unblockAll();
        assertThat(registry.blockedRouteIds()).isEmpty();
    }

    @Test
    void blockedRouteIds_returnsSnapshot() {
        registry.block("route-x");
        assertThat(registry.blockedRouteIds()).containsExactly("route-x");
    }
}
