package com.sentinelgateway.gateway.admin;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
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
 * Integration tests for {@link LiveDashboardController} (Phase 45: Live Operations Dashboard).
 *
 * Scenarios:
 *   1. GET /admin/dashboard returns all expected top-level sections
 *   2. health section contains score and grade
 *   3. uptime section contains uptimeSeconds
 *   4. Requires ROLE_ADMIN
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class LiveDashboardControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

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

    private WebTestClient userClient() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void getDashboard_returnsAllTopLevelSections() {
        adminClient().get().uri("/admin/dashboard")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys(
                            "timestamp", "health", "uptime", "concurrency",
                            "errors", "statusCodes", "latency", "slowRequests",
                            "sessions", "sizes", "hops");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void health_section_containsScoreAndGrade() {
        adminClient().get().uri("/admin/dashboard")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    Map<String, Object> health = (Map<String, Object>) body.get("health");
                    assertThat(health).containsKeys("score", "grade", "factors");
                    int score = ((Number) health.get("score")).intValue();
                    assertThat(score).isBetween(0, 100);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void uptime_section_containsUptimeSeconds() {
        adminClient().get().uri("/admin/dashboard")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    Map<String, Object> uptime = (Map<String, Object>) body.get("uptime");
                    assertThat(uptime).containsKeys("startTime", "uptimeSeconds", "totalRequests");
                    assertThat(((Number) uptime.get("uptimeSeconds")).longValue()).isGreaterThanOrEqualTo(0L);
                });
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getDashboard_userRole_returns403() {
        userClient().get().uri("/admin/dashboard")
                .exchange()
                .expectStatus().isForbidden();
    }
}
