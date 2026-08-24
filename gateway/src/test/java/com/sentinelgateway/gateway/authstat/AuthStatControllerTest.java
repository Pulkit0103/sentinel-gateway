package com.sentinelgateway.gateway.authstat;

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
 * Integration + unit tests for Authorization Header Type Distribution (Phase 67).
 *
 * Scenarios:
 *   1. GET /admin/auth-stats returns expected fields
 *   2. Seeded records are classified correctly (bearer/basic/none/other)
 *   3. POST /admin/auth-stats/reset clears counters
 *   4. Requires ROLE_ADMIN
 *   5. classify() boundary conditions: null→none, blank→none, bearer/basic/apikey/other
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AuthStatControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AuthStatRegistry registry;

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
        adminClient().get().uri("/admin/auth-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("enabled", "total", "types");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> types = (Map<String, Object>) body.get("types");
                    assertThat(types).containsKeys("none", "bearer", "apikey", "basic", "other");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void seededRecords_classifiedCorrectly() {
        registry.record("Bearer eyJhbGci...");   // bearer
        registry.record("Bearer eyJhbGci...");   // bearer
        registry.record("Basic dXNlcjpwYXNz");  // basic
        registry.record(null);                   // none
        registry.record("Token xyz");            // other

        adminClient().get().uri("/admin/auth-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("total")).longValue()).isEqualTo(5L);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> types = (Map<String, Object>) body.get("types");
                    assertThat(((Number) types.get("bearer")).longValue()).isEqualTo(2L);
                    assertThat(((Number) types.get("basic")).longValue()).isEqualTo(1L);
                    assertThat(((Number) types.get("none")).longValue()).isEqualTo(1L);
                    assertThat(((Number) types.get("other")).longValue()).isEqualTo(1L);
                    assertThat(((Number) types.get("apikey")).longValue()).isEqualTo(0L);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void resetEndpoint_clearsCounters() {
        registry.record("Bearer token");
        registry.record(null);

        adminClient().post().uri("/admin/auth-stats/reset")
                .exchange()
                .expectStatus().isOk();

        Map<String, Object> snap = registry.snapshot();
        assertThat(((Number) snap.get("total")).longValue()).isZero();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getStats_userRole_returns403() {
        userClient().get().uri("/admin/auth-stats")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void classify_boundaryConditions() {
        assertThat(AuthStatRegistry.classify(null)).isEqualTo("none");
        assertThat(AuthStatRegistry.classify("   ")).isEqualTo("none");
        assertThat(AuthStatRegistry.classify("Bearer abc")).isEqualTo("bearer");
        assertThat(AuthStatRegistry.classify("BEARER abc")).isEqualTo("bearer");
        assertThat(AuthStatRegistry.classify("Basic abc")).isEqualTo("basic");
        assertThat(AuthStatRegistry.classify("ApiKey abc")).isEqualTo("apikey");
        assertThat(AuthStatRegistry.classify("API-Key abc")).isEqualTo("apikey");
        assertThat(AuthStatRegistry.classify("Digest abc")).isEqualTo("other");
    }
}
