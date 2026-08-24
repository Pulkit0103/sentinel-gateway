package com.sentinelgateway.gateway.headeraudit;

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
 * Integration + unit tests for response header audit (Phase 51).
 *
 * Scenarios:
 *   1. GET /admin/header-audit returns expected fields
 *   2. Seeded present/absent counts appear correctly in snapshot
 *   3. Coverage percentage computed correctly
 *   4. POST /admin/header-audit/reset clears counters
 *   5. Requires ROLE_ADMIN
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class HeaderAuditControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private HeaderAuditRegistry registry;

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
        adminClient().get().uri("/admin/header-audit")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys(
                            "enabled", "trackedHeaders", "totalResponses",
                            "coveragePercent", "headers");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void seededCounts_appearInSnapshot() {
        registry.incrementResponses();
        registry.incrementResponses();
        registry.record("X-Frame-Options", true);
        registry.record("X-Frame-Options", true);
        registry.record("Cache-Control", false);
        registry.record("Cache-Control", false);

        adminClient().get().uri("/admin/header-audit")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("totalResponses")).longValue()).isEqualTo(2L);
                    Map<String, Object> headers = (Map<String, Object>) body.get("headers");
                    Map<String, Object> xfo = (Map<String, Object>) headers.get("X-Frame-Options");
                    assertThat(((Number) xfo.get("present")).longValue()).isEqualTo(2L);
                    assertThat(((Number) xfo.get("absent")).longValue()).isEqualTo(0L);
                    Map<String, Object> cc = (Map<String, Object>) headers.get("Cache-Control");
                    assertThat(((Number) cc.get("present")).longValue()).isEqualTo(0L);
                    assertThat(((Number) cc.get("absent")).longValue()).isEqualTo(2L);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void coveragePercent_computedCorrectly() {
        // 3 present, 1 absent → 75%
        registry.record("H1", true);
        registry.record("H1", true);
        registry.record("H2", true);
        registry.record("H2", false);

        Map<String, Object> snap = registry.snapshot();
        double coverage = ((Number) snap.get("coveragePercent")).doubleValue();
        assertThat(coverage).isEqualTo(75.0);
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void resetEndpoint_clearsCounters() {
        registry.incrementResponses();
        registry.record("X-Frame-Options", true);

        adminClient().post().uri("/admin/header-audit/reset")
                .exchange()
                .expectStatus().isOk();

        assertThat(registry.getTotalResponses()).isZero();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void getStats_userRole_returns403() {
        userClient().get().uri("/admin/header-audit")
                .exchange()
                .expectStatus().isForbidden();
    }
}
