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
 * Integration tests for {@link ConfigSummaryController} (Phase 34: Config Summary Dashboard).
 *
 * Scenarios:
 *   1. GET /admin/config returns all required top-level keys
 *   2. Features map contains all expected feature toggle keys
 *   3. Stats map contains expected metric keys
 *   4. Requires ROLE_ADMIN
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ConfigSummaryControllerTest {

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
    void getConfig_admin_returnsTopLevelKeys() {
        adminClient().get().uri("/admin/config")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("startTime", "features", "stats");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void getConfig_featuresMap_containsAllToggles() {
        adminClient().get().uri("/admin/config")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    Map<String, Object> features = (Map<String, Object>) body.get("features");
                    assertThat(features).containsKeys(
                            "maintenance", "ipAccess", "securityHeaders", "requestSanitizer",
                            "slowRequestDetection", "slaTracking", "claimAuthorization",
                            "jwtAudienceValidation");
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void getConfig_statsMap_containsMetricKeys() {
        adminClient().get().uri("/admin/config")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    Map<String, Object> stats = (Map<String, Object>) body.get("stats");
                    assertThat(stats).containsKeys(
                            "uptimeSeconds", "requestCount",
                            "totalTrackedSessions", "inflightRequests", "totalCompletedRequests");
                    assertThat(((Number) stats.get("uptimeSeconds")).longValue())
                            .isGreaterThanOrEqualTo(0);
                });
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getConfig_userRole_returns403() {
        userClient().get().uri("/admin/config")
                .exchange()
                .expectStatus().isForbidden();
    }
}
