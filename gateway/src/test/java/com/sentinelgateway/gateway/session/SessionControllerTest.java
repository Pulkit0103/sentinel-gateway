package com.sentinelgateway.gateway.session;

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
 * Integration tests for {@link SessionController} (Phase 27: Active Session Tracking).
 *
 * Scenarios:
 *   1. GET /admin/sessions returns 200 with expected fields
 *   2. GET /admin/sessions with custom window returns different window in response
 *   3. POST /admin/sessions/evict returns evicted count
 *   4. GET requires ADMIN role
 *   5. Evict requires ADMIN role
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SessionControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ActiveSessionRegistry registry;

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
        registry.clear();
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
    void getActiveSessions_admin_returns200WithExpectedFields() {
        adminClient().get().uri("/admin/sessions")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys(
                            "windowSeconds", "activeSessions", "subjects", "totalTracked");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void getActiveSessions_customWindow_reflected() {
        adminClient().get().uri("/admin/sessions?windowSeconds=60")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("windowSeconds")).longValue()).isEqualTo(60L);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void seededSubject_appearsInActiveSessions() {
        registry.recordActivity("test-subject-xyz");

        adminClient().get().uri("/admin/sessions")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("activeSessions")).intValue()).isGreaterThan(0);
                });
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void evict_admin_returnsEvictedCount() {
        registry.recordActivity("evict-me");

        adminClient().post().uri("/admin/sessions/evict?olderThanSeconds=0")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("evicted", "remaining");
                });
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void getActiveSessions_userRole_returns403() {
        userClient().get().uri("/admin/sessions")
                .exchange()
                .expectStatus().isForbidden();
    }
}
