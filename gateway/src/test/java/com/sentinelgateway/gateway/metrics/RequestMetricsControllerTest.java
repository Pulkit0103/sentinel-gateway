package com.sentinelgateway.gateway.metrics;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RequestMetricsController} (Phase 23: Active Request Metrics).
 *
 * Scenarios:
 *   1. GET /admin/request-metrics returns 200 with expected fields
 *   2. After sending requests, totalCompleted increases
 *   3. POST /admin/request-metrics/reset clears counters
 *   4. GET /admin/request-metrics requires ADMIN role
 *   5. Reset requires ADMIN role
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RequestMetricsControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RequestMetricsRegistry registry;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry reg) {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));

        String base = "http://localhost:" + wireMock.port();
        reg.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> base + "/jwks");
        reg.add("sentinel.security.jwt.issuer", () -> "");

        reg.add("sentinel.gateway.routes[0].route-id",    () -> "metrics-ctrl-test");
        reg.add("sentinel.gateway.routes[0].path",        () -> "/api/metrics-ctrl/**");
        reg.add("sentinel.gateway.routes[0].service-uri", () -> base);
        reg.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        reg.add("sentinel.gateway.routes[0].enabled",     () -> "true");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));
        wireMock.stubFor(get(urlPathMatching("/api/metrics-ctrl/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));
    }

    @AfterEach
    void resetRegistry() {
        registry.reset();
    }

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private WebTestClient userClient() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void getMetrics_admin_returns200WithExpectedFields() {
        adminClient().get().uri("/admin/request-metrics")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys(
                            "inflightRequests", "totalCompleted", "perRouteCompleted");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void afterRequest_totalCompletedIncreases() {
        registry.reset();

        userClient().get().uri("/api/metrics-ctrl/ping")
                .exchange()
                .expectStatus().isOk();

        adminClient().get().uri("/admin/request-metrics")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    long total = ((Number) body.get("totalCompleted")).longValue();
                    assertThat(total).isGreaterThan(0);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void reset_admin_clearsCounters() {
        registry.requestStarted();
        registry.requestCompleted("some-route");

        adminClient().post().uri("/admin/request-metrics/reset")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body.get("reset")).isEqualTo(true));

        assertThat(registry.getTotalCompleted()).isZero();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getMetrics_userRole_returns403() {
        userClient().get().uri("/admin/request-metrics")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void reset_userRole_returns403() {
        userClient().post().uri("/admin/request-metrics/reset")
                .exchange()
                .expectStatus().isForbidden();
    }
}
