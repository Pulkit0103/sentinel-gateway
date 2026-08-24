package com.sentinelgateway.gateway.headercountdist;

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
 * Integration + unit tests for request header count distribution (Phase 58).
 *
 * Scenarios:
 *   1. GET /admin/header-count-stats returns expected fields
 *   2. Seeded counts appear in correct buckets with maxSeen tracked
 *   3. POST /admin/header-count-stats/reset clears counters
 *   4. Requires ROLE_ADMIN
 *   5. HeaderCountRegistry.classify covers all bucket boundaries
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class HeaderCountControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private HeaderCountRegistry registry;

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
        adminClient().get().uri("/admin/header-count-stats")
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
        registry.record(3);   // low
        registry.record(10);  // normal
        registry.record(10);  // normal
        registry.record(25);  // elevated
        registry.record(50);  // high

        adminClient().get().uri("/admin/header-count-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("total")).longValue()).isEqualTo(5L);
                    assertThat(((Number) body.get("maxSeen")).longValue()).isEqualTo(50L);
                    Map<String, Object> buckets = (Map<String, Object>) body.get("buckets");
                    assertThat(((Number) buckets.get("low")).longValue()).isEqualTo(1L);
                    assertThat(((Number) buckets.get("normal")).longValue()).isEqualTo(2L);
                    assertThat(((Number) buckets.get("elevated")).longValue()).isEqualTo(1L);
                    assertThat(((Number) buckets.get("high")).longValue()).isEqualTo(1L);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void resetEndpoint_clearsCounters() {
        registry.record(10);

        adminClient().post().uri("/admin/header-count-stats/reset")
                .exchange()
                .expectStatus().isOk();

        assertThat(registry.getTotal()).isZero();
        assertThat(registry.getMaxSeen()).isZero();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getStats_userRole_returns403() {
        userClient().get().uri("/admin/header-count-stats")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void classify_coversAllBucketBoundaries() {
        assertThat(HeaderCountRegistry.classify(1)).isEqualTo("low");
        assertThat(HeaderCountRegistry.classify(5)).isEqualTo("low");
        assertThat(HeaderCountRegistry.classify(6)).isEqualTo("normal");
        assertThat(HeaderCountRegistry.classify(15)).isEqualTo("normal");
        assertThat(HeaderCountRegistry.classify(16)).isEqualTo("elevated");
        assertThat(HeaderCountRegistry.classify(30)).isEqualTo("elevated");
        assertThat(HeaderCountRegistry.classify(31)).isEqualTo("high");
        assertThat(HeaderCountRegistry.classify(1000)).isEqualTo("high");
    }
}
