package com.sentinelgateway.gateway.slowrequest;

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

import java.time.Instant;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SlowRequestController} (Phase 33: Slow Request Detection).
 *
 * Scenarios:
 *   1. GET /admin/slow-requests returns expected fields
 *   2. Seeded records appear in the log
 *   3. POST /admin/slow-requests/clear empties the log
 *   4. Requires ROLE_ADMIN
 *   5. Disabled filter config reflected in response
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SlowRequestControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private SlowRequestRegistry registry;

    @Autowired
    private SlowRequestProperties properties;

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

    @AfterEach
    void reset() {
        registry.clear();
        properties.setEnabled(true);
        properties.setThresholdMs(2000);
    }

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private WebTestClient userClient() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    private SlowRequestRecord rec(String path, long ms) {
        return new SlowRequestRecord(Instant.now(), "GET", path, ms, 200);
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void getSlowRequests_admin_returnsExpectedFields() {
        adminClient().get().uri("/admin/slow-requests")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("enabled", "thresholdMs", "total", "records");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void seededRecords_appearsInLog() {
        registry.record(rec("/api/slow-endpoint", 5000));
        registry.record(rec("/api/very-slow", 10000));

        adminClient().get().uri("/admin/slow-requests")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("total")).intValue()).isEqualTo(2);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void clearLog_emptiesRegistry() {
        registry.record(rec("/api/slow", 3000));

        adminClient().post().uri("/admin/slow-requests/clear")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("remaining")).intValue()).isZero();
                });

        assertThat(registry.size()).isZero();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getSlowRequests_userRole_returns403() {
        userClient().get().uri("/admin/slow-requests")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void filterDisabledConfig_reflectedInResponse() {
        properties.setEnabled(false);

        adminClient().get().uri("/admin/slow-requests")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body.get("enabled")).isEqualTo(false);
                });
    }
}
