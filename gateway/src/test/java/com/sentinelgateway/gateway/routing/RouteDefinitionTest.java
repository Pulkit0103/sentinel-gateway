package com.sentinelgateway.gateway.routing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RouteDefinitionTest {

    @Test
    void constructor_setsAllFields() {
        RouteDefinition route = new RouteDefinition(
                "user-service", "/api/users/**",
                "http://localhost:8082",
                List.of("GET", "POST"), true,
                List.of("user:read"), false, "DEFAULT");

        assertThat(route.routeId()).isEqualTo("user-service");
        assertThat(route.path()).isEqualTo("/api/users/**");
        assertThat(route.serviceUri()).isEqualTo("http://localhost:8082");
        assertThat(route.methods()).containsExactly("GET", "POST");
        assertThat(route.enabled()).isTrue();
        assertThat(route.requiredScopes()).containsExactly("user:read");
        assertThat(route.tenantRequired()).isFalse();
        assertThat(route.rateLimitPolicy()).isEqualTo("DEFAULT");
    }

    @Test
    void constructor_nullMethods_defaultsToEmptyList() {
        RouteDefinition route = new RouteDefinition(
                "r1", "/api/test/**", "http://localhost:9000",
                null, true, null, false, null);

        assertThat(route.methods()).isEmpty();
        assertThat(route.requiredScopes()).isEmpty();
        assertThat(route.rateLimitPolicy()).isEqualTo("DEFAULT");
    }

    @Test
    void constructor_nullRouteId_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RouteDefinition(
                        null, "/api/test/**", "http://localhost:9000",
                        List.of(), true, List.of(), false, "DEFAULT"))
                .withMessageContaining("routeId");
    }

    @Test
    void constructor_nullPath_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RouteDefinition(
                        "r1", null, "http://localhost:9000",
                        List.of(), true, List.of(), false, "DEFAULT"))
                .withMessageContaining("path");
    }

    @Test
    void constructor_nullServiceUri_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RouteDefinition(
                        "r1", "/api/test/**", null,
                        List.of(), true, List.of(), false, "DEFAULT"))
                .withMessageContaining("serviceUri");
    }

    @Test
    void methods_listIsImmutable() {
        RouteDefinition route = new RouteDefinition(
                "r1", "/api/**", "http://localhost:9000",
                List.of("GET"), true, List.of(), false, "DEFAULT");

        assertThatThrownBy(() -> route.methods().add("POST"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equality_basedOnRouteIdOnly() {
        RouteDefinition a = new RouteDefinition("r1", "/a/**", "http://a", List.of(), true, List.of(), false, "X");
        RouteDefinition b = new RouteDefinition("r1", "/b/**", "http://b", List.of(), false, List.of(), true, "Y");
        RouteDefinition c = new RouteDefinition("r2", "/a/**", "http://a", List.of(), true, List.of(), false, "X");

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void disabledRoute_hasEnabledFalse() {
        RouteDefinition route = new RouteDefinition(
                "legacy", "/api/legacy/**", "http://localhost:9999",
                List.of("GET"), false, List.of(), false, "DEFAULT");

        assertThat(route.enabled()).isFalse();
    }
}
