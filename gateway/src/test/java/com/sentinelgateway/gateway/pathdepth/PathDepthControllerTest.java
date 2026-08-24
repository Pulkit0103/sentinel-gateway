package com.sentinelgateway.gateway.pathdepth;

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
 * Integration + unit tests for URL path-depth distribution (Phase 62).
 *
 * Scenarios:
 *   1. GET /admin/path-depth-stats returns expected fields
 *   2. Seeded depths appear in correct buckets with maxSeen tracked
 *   3. POST /admin/path-depth-stats/reset clears counters
 *   4. Requires ROLE_ADMIN
 *   5. countSegments and classify cover all boundaries
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PathDepthControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PathDepthRegistry registry;

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
        adminClient().get().uri("/admin/path-depth-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("enabled", "total", "maxSeen", "buckets");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void seededDepths_appearInCorrectBucketsWithMaxSeen() {
        registry.record(0);  // root
        registry.record(1);  // shallow
        registry.record(2);  // shallow
        registry.record(3);  // moderate
        registry.record(6);  // deep

        adminClient().get().uri("/admin/path-depth-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("total")).longValue()).isEqualTo(5L);
                    assertThat(((Number) body.get("maxSeen")).longValue()).isEqualTo(6L);
                    Map<String, Object> buckets = (Map<String, Object>) body.get("buckets");
                    assertThat(((Number) buckets.get("root")).longValue()).isEqualTo(1L);
                    assertThat(((Number) buckets.get("shallow")).longValue()).isEqualTo(2L);
                    assertThat(((Number) buckets.get("moderate")).longValue()).isEqualTo(1L);
                    assertThat(((Number) buckets.get("deep")).longValue()).isEqualTo(1L);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void resetEndpoint_clearsCounters() {
        registry.record(3);

        adminClient().post().uri("/admin/path-depth-stats/reset")
                .exchange()
                .expectStatus().isOk();

        assertThat(registry.getTotal()).isZero();
        assertThat(registry.getMaxSeen()).isZero();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getStats_userRole_returns403() {
        userClient().get().uri("/admin/path-depth-stats")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void segmentCountAndClassify_coverAllBoundaries() {
        assertThat(PathDepthRegistry.countSegments("/")).isEqualTo(0);
        assertThat(PathDepthRegistry.countSegments("")).isEqualTo(0);
        assertThat(PathDepthRegistry.countSegments("/api")).isEqualTo(1);
        assertThat(PathDepthRegistry.countSegments("/api/users")).isEqualTo(2);
        assertThat(PathDepthRegistry.countSegments("/api/users/123")).isEqualTo(3);
        assertThat(PathDepthRegistry.countSegments("/api/users/123/orders")).isEqualTo(4);
        assertThat(PathDepthRegistry.countSegments("/a/b/c/d/e")).isEqualTo(5);

        assertThat(PathDepthRegistry.classify(0)).isEqualTo("root");
        assertThat(PathDepthRegistry.classify(1)).isEqualTo("shallow");
        assertThat(PathDepthRegistry.classify(2)).isEqualTo("shallow");
        assertThat(PathDepthRegistry.classify(3)).isEqualTo("moderate");
        assertThat(PathDepthRegistry.classify(4)).isEqualTo("moderate");
        assertThat(PathDepthRegistry.classify(5)).isEqualTo("deep");
        assertThat(PathDepthRegistry.classify(100)).isEqualTo("deep");
    }
}
