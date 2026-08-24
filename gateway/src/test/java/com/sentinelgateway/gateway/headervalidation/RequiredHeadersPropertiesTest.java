package com.sentinelgateway.gateway.headervalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RequiredHeadersProperties} (Phase 19: Required Header Validation).
 */
class RequiredHeadersPropertiesTest {

    @Test
    void defaults_enabledIsFalse() {
        assertThat(new RequiredHeadersProperties().isEnabled()).isFalse();
    }

    @Test
    void defaults_routesMapIsEmpty() {
        assertThat(new RequiredHeadersProperties().getRoutes()).isEmpty();
    }

    @Test
    void setEnabled_updatesFlag() {
        RequiredHeadersProperties props = new RequiredHeadersProperties();
        props.setEnabled(true);
        assertThat(props.isEnabled()).isTrue();
    }

    @Test
    void setRoutes_storesRouteMap() {
        RequiredHeadersProperties props = new RequiredHeadersProperties();
        props.setRoutes(Map.of("my-route", List.of("X-Api-Key", "X-Request-Id")));
        assertThat(props.getRoutes()).containsKey("my-route");
        assertThat(props.getRoutes().get("my-route")).containsExactly("X-Api-Key", "X-Request-Id");
    }

    @Test
    void requiredHeadersForRoute_knownRoute_returnsConfiguredHeaders() {
        RequiredHeadersProperties props = new RequiredHeadersProperties();
        props.setRoutes(Map.of("payment-service", List.of("X-Idempotency-Key")));
        assertThat(props.requiredHeadersForRoute("payment-service"))
                .containsExactly("X-Idempotency-Key");
    }

    @Test
    void requiredHeadersForRoute_unknownRoute_returnsEmptyList() {
        RequiredHeadersProperties props = new RequiredHeadersProperties();
        assertThat(props.requiredHeadersForRoute("unknown-route")).isEmpty();
    }

    @Test
    void requiredHeadersForRoute_nullRouteId_returnsEmptyList() {
        assertThat(new RequiredHeadersProperties().requiredHeadersForRoute(null)).isEmpty();
    }
}
