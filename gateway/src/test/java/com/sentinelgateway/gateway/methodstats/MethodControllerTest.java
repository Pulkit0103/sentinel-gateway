package com.sentinelgateway.gateway.methodstats;

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
 * Integration tests for {@link MethodController} (Phase 46: HTTP Method Distribution).
 *
 * Scenarios:
 *   1. GET /admin/method-stats returns expected fields
 *   2. Seeded method counts appear in snapshot
 *   3. POST /admin/method-stats/reset clears counters
 *   4. Requires ROLE_ADMIN
 *   5. MethodRegistry unit: record and snapshot
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class MethodControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MethodRegistry registry;

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
    void clearRegistry() {
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
    void getStats_returnsExpectedFields() {
        adminClient().get().uri("/admin/method-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("enabled", "total", "methods");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void seededCounts_appearInSnapshot() {
        registry.record("GET");
        registry.record("GET");
        registry.record("POST");

        adminClient().get().uri("/admin/method-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("total")).longValue()).isGreaterThanOrEqualTo(3L);
                    Map<String, Object> methods = (Map<String, Object>) body.get("methods");
                    assertThat(((Number) methods.get("GET")).longValue()).isGreaterThanOrEqualTo(2L);
                    assertThat(((Number) methods.get("POST")).longValue()).isGreaterThanOrEqualTo(1L);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void resetEndpoint_clearsCounters() {
        registry.record("GET");
        registry.record("POST");

        adminClient().post().uri("/admin/method-stats/reset")
                .exchange()
                .expectStatus().isOk();

        Map<String, Object> snap = registry.snapshot();
        assertThat(((Number) snap.get("total")).longValue()).isZero();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getStats_userRole_returns403() {
        userClient().get().uri("/admin/method-stats")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void methodRegistry_recordsAndResets() {
        registry.record("DELETE");
        registry.record("PATCH");
        registry.record("DELETE");

        Map<String, Object> snap = registry.snapshot();
        assertThat(((Number) snap.get("total")).longValue()).isEqualTo(3L);
        Map<String, Long> methods = (Map<String, Long>) snap.get("methods");
        assertThat(methods.get("DELETE")).isEqualTo(2L);
        assertThat(methods.get("PATCH")).isEqualTo(1L);

        registry.reset();
        assertThat(((Number) registry.snapshot().get("total")).longValue()).isZero();
    }
}
