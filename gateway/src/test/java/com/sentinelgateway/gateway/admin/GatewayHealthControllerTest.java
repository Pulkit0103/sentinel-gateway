package com.sentinelgateway.gateway.admin;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Phase 13: Admin Dashboard – Gateway Health Aggregation.
 *
 * Uses {@code mockJwt()} with {@code ROLE_ADMIN} so the existing
 * SecurityWebFilterChain ADMIN check is satisfied without a real JWKS server.
 *
 * A route is seeded via the Route Admin API in {@code @BeforeEach} so the
 * health endpoints always have at least one entry to aggregate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayHealthControllerTest {

    private static WireMockServer wireMock;

    /** Unique suffix per test run to avoid PK conflicts across test re-runs. */
    private static final String ROUTE_ID = "health-test-route-" + System.currentTimeMillis();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + wireMock.port() + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    /**
     * Seed one route before each test so the health endpoints always have data.
     * Uses PUT-or-create: if the route already exists the create call fails silently
     * (unique-constraint violation returns 409 which we ignore).
     */
    @BeforeEach
    void seedRoute() {
        adminClient().post().uri("/admin/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "routeId", ROUTE_ID,
                        "path", "/api/health-test/**",
                        "serviceUri", "http://health-test-service:8080",
                        "enabled", true,
                        "rateLimitPolicy", "DEFAULT"
                ))
                .exchange()
                // 201 Created on first run, 409 Conflict on subsequent runs — both are fine
                .expectStatus().value(status ->
                        assertThat(status).isIn(201, 409));
    }

    // ── Test 1: GET /admin/health/routes ─────────────────────────────────────

    @Test
    void getRoutes_returnsListWithSeededRoutes() {
        adminClient().get().uri("/admin/health/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(list -> {
                    assertThat(list).isNotEmpty();

                    // The seeded route must appear in the list
                    boolean seededFound = list.stream()
                            .anyMatch(r -> ROUTE_ID.equals(r.get("routeId")));
                    assertThat(seededFound).isTrue();

                    // Every report must carry the mandatory fields
                    Map<String, Object> first = list.get(0);
                    assertThat(first).containsKey("routeId");
                    assertThat(first).containsKey("path");
                    assertThat(first).containsKey("serviceUri");
                    assertThat(first).containsKey("enabled");
                    assertThat(first).containsKey("rateLimitPolicy");
                    assertThat(first).containsKey("canaryEnabled");
                    assertThat(first).containsKey("ipFilterEnabled");
                });
    }

    // ── Test 2: GET /admin/health/summary ────────────────────────────────────

    @Test
    void getSummary_returnsAggregatedCounts() {
        adminClient().get().uri("/admin/health/summary")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKey("totalRoutes");
                    int total = ((Number) body.get("totalRoutes")).intValue();
                    assertThat(total).isGreaterThanOrEqualTo(1);

                    assertThat(body).containsKey("enabledRoutes");
                    assertThat(body).containsKey("disabledRoutes");
                    assertThat(body).containsKey("routesWithCanary");
                    assertThat(body).containsKey("routesWithCache");
                    assertThat(body).containsKey("routesWithIpFilter");
                    assertThat(body).containsKey("routesWithTimeout");

                    int enabled = ((Number) body.get("enabledRoutes")).intValue();
                    int disabled = ((Number) body.get("disabledRoutes")).intValue();
                    assertThat(enabled + disabled).isEqualTo(total);
                });
    }

    // ── Test 3: Unauthenticated request returns 401 ───────────────────────────

    @Test
    void getRoutes_unauthenticated_returns401() {
        webTestClient.get().uri("/admin/health/routes")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── Test 4: Non-admin role returns 403 ────────────────────────────────────

    @Test
    void getRoutes_nonAdminRole_returns403() {
        webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .get().uri("/admin/health/routes")
                .exchange()
                .expectStatus().isForbidden();
    }
}
