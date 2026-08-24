package com.sentinelgateway.gateway.uptime;

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
 * Integration tests for {@link UptimeController} (Phase 30: Gateway Uptime Tracking).
 *
 * Scenarios:
 *   1. GET /admin/uptime returns expected fields
 *   2. GET /admin/uptime requestCount increments after non-admin requests
 *   3. POST /admin/uptime/reset clears requestCount
 *   4. Admin endpoint requires ROLE_ADMIN
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class UptimeControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private UptimeRegistry registry;

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
    void getUptime_admin_returnsExpectedFields() {
        adminClient().get().uri("/admin/uptime")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("startTime", "uptimeSeconds", "requestCount");
                    assertThat(((Number) body.get("uptimeSeconds")).longValue()).isGreaterThanOrEqualTo(0);
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void resetCounter_returnsZeroRequestCount() {
        registry.recordRequest();
        registry.recordRequest();

        adminClient().post().uri("/admin/uptime/reset")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body.get("reset")).isEqualTo(true);
                    assertThat(((Number) body.get("requestCount")).intValue()).isZero();
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void seededRequests_appearsInRequestCount() {
        registry.recordRequest();
        registry.recordRequest();
        registry.recordRequest();

        adminClient().get().uri("/admin/uptime")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("requestCount")).intValue()).isGreaterThanOrEqualTo(3);
                });
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getUptime_userRole_returns403() {
        userClient().get().uri("/admin/uptime")
                .exchange()
                .expectStatus().isForbidden();
    }
}
