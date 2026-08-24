package com.sentinelgateway.gateway.sla;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
 * Integration tests for {@link SlaController} (Phase 25: Response Time SLA Tracking).
 *
 * Scenarios:
 *   1. GET /admin/sla returns 200 with expected fields
 *   2. SLA stats appear after requests to tracked routes
 *   3. POST /admin/sla/reset clears stats
 *   4. GET requires ADMIN role
 *   5. Reset requires ADMIN role
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SlaControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private SlaTracker slaTracker;

    @Autowired
    private SlaProperties slaProperties;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));

        String base = "http://localhost:" + wireMock.port();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "");

        // Enable SLA tracking with a generous target
        registry.add("sentinel.sla.enabled", () -> "true");
        registry.add("sentinel.sla.routes.sla-test-route", () -> "5000"); // 5s target — easy to meet

        registry.add("sentinel.gateway.routes[0].route-id",    () -> "sla-test-route");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/sla-test/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    @AfterEach
    void resetTracker() {
        slaTracker.reset();
        wireMock.resetAll();
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));
        wireMock.stubFor(get(urlPathMatching("/api/sla-test/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));
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
    void getSlaReport_admin_returns200WithExpectedFields() {
        adminClient().get().uri("/admin/sla")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("enabled", "configuredRoutes", "statistics");
                    assertThat(body.get("enabled")).isEqualTo(true);
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void afterRequest_slaStatisticsContainRoute() {
        wireMock.stubFor(get(urlPathMatching("/api/sla-test/.*"))
                .willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));

        userClient().get().uri("/api/sla-test/resource")
                .exchange()
                .expectStatus().isOk();

        adminClient().get().uri("/admin/sla")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stats = (Map<String, Object>) body.get("statistics");
                    assertThat(stats).containsKey("sla-test-route");
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void resetSla_admin_clearsStatistics() {
        slaTracker.record("sla-test-route", 100, 5000);

        adminClient().post().uri("/admin/sla/reset")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body.get("reset")).isEqualTo(true));

        assertThat(slaTracker.snapshots()).isEmpty();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getSlaReport_userRole_returns403() {
        userClient().get().uri("/admin/sla")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void resetSla_userRole_returns403() {
        userClient().post().uri("/admin/sla/reset")
                .exchange()
                .expectStatus().isForbidden();
    }
}
