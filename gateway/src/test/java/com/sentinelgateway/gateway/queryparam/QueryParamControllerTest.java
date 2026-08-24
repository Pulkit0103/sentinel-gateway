package com.sentinelgateway.gateway.queryparam;

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
 * Integration + unit tests for query parameter count distribution (Phase 60).
 *
 * Scenarios:
 *   1. GET /admin/query-param-stats returns expected fields
 *   2. Seeded counts appear in correct buckets with maxSeen tracked
 *   3. POST /admin/query-param-stats/reset clears counters
 *   4. Requires ROLE_ADMIN
 *   5. QueryParamRegistry.classify covers all bucket boundaries
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class QueryParamControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private QueryParamRegistry registry;

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
        adminClient().get().uri("/admin/query-param-stats")
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
    void seededCounts_appearInCorrectBucketsWithMaxSeen() {
        registry.record(0);   // zero
        registry.record(1);   // few
        registry.record(3);   // few
        registry.record(5);   // moderate
        registry.record(12);  // many

        adminClient().get().uri("/admin/query-param-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("total")).longValue()).isEqualTo(5L);
                    assertThat(((Number) body.get("maxSeen")).longValue()).isEqualTo(12L);
                    Map<String, Object> buckets = (Map<String, Object>) body.get("buckets");
                    assertThat(((Number) buckets.get("zero")).longValue()).isEqualTo(1L);
                    assertThat(((Number) buckets.get("few")).longValue()).isEqualTo(2L);
                    assertThat(((Number) buckets.get("moderate")).longValue()).isEqualTo(1L);
                    assertThat(((Number) buckets.get("many")).longValue()).isEqualTo(1L);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void resetEndpoint_clearsCounters() {
        registry.record(5);

        adminClient().post().uri("/admin/query-param-stats/reset")
                .exchange()
                .expectStatus().isOk();

        assertThat(registry.getTotal()).isZero();
        assertThat(registry.getMaxSeen()).isZero();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getStats_userRole_returns403() {
        userClient().get().uri("/admin/query-param-stats")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void classify_coversAllBucketBoundaries() {
        assertThat(QueryParamRegistry.classify(0)).isEqualTo("zero");
        assertThat(QueryParamRegistry.classify(1)).isEqualTo("few");
        assertThat(QueryParamRegistry.classify(3)).isEqualTo("few");
        assertThat(QueryParamRegistry.classify(4)).isEqualTo("moderate");
        assertThat(QueryParamRegistry.classify(8)).isEqualTo("moderate");
        assertThat(QueryParamRegistry.classify(9)).isEqualTo("many");
        assertThat(QueryParamRegistry.classify(100)).isEqualTo("many");
    }
}
