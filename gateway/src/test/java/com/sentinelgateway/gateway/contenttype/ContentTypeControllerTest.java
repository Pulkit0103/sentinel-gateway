package com.sentinelgateway.gateway.contenttype;

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
 * Integration + unit tests for Content-Type distribution (Phase 47).
 *
 * Scenarios:
 *   1. GET /admin/content-type-stats returns expected fields
 *   2. Seeded content types appear in snapshot (normalised)
 *   3. POST /admin/content-type-stats/reset clears counters
 *   4. Requires ROLE_ADMIN
 *   5. ContentTypeRegistry.normalise strips parameters
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ContentTypeControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ContentTypeRegistry registry;

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
        adminClient().get().uri("/admin/content-type-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("enabled", "total", "types");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void seededTypes_appearNormalisedInSnapshot() {
        registry.record("application/json;charset=UTF-8");
        registry.record("application/json");
        registry.record("text/plain");

        adminClient().get().uri("/admin/content-type-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("total")).longValue()).isEqualTo(3L);
                    Map<String, Object> types = (Map<String, Object>) body.get("types");
                    // Both "application/json;charset=UTF-8" and "application/json" → "application/json"
                    assertThat(((Number) types.get("application/json")).longValue()).isEqualTo(2L);
                    assertThat(((Number) types.get("text/plain")).longValue()).isEqualTo(1L);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void resetEndpoint_clearsCounters() {
        registry.record("application/json");

        adminClient().post().uri("/admin/content-type-stats/reset")
                .exchange()
                .expectStatus().isOk();

        assertThat(((Number) registry.snapshot().get("total")).longValue()).isZero();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getStats_userRole_returns403() {
        userClient().get().uri("/admin/content-type-stats")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void normalise_stripsParametersAndLowercases() {
        assertThat(ContentTypeRegistry.normalise("application/json;charset=UTF-8"))
                .isEqualTo("application/json");
        assertThat(ContentTypeRegistry.normalise("TEXT/HTML; charset=utf-8"))
                .isEqualTo("text/html");
        assertThat(ContentTypeRegistry.normalise(null)).isEqualTo("unknown");
        assertThat(ContentTypeRegistry.normalise("")).isEqualTo("unknown");
    }
}
