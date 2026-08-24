package com.sentinelgateway.gateway.responseheadersize;

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
 * Integration + unit tests for response header size distribution (Phase 59).
 *
 * Scenarios:
 *   1. GET /admin/response-header-size-stats returns expected fields
 *   2. Seeded size values appear in correct buckets with maxSeen tracked
 *   3. POST /admin/response-header-size-stats/reset clears counters
 *   4. Requires ROLE_ADMIN
 *   5. ResponseHeaderSizeRegistry.classify covers all bucket boundaries
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ResponseHeaderSizeControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ResponseHeaderSizeRegistry registry;

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
        adminClient().get().uri("/admin/response-header-size-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("enabled", "total", "maxSeenBytes", "buckets");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void seededSizes_appearInCorrectBucketsWithMaxSeen() {
        registry.record(100L);   // small
        registry.record(512L);   // small
        registry.record(1000L);  // medium
        registry.record(5000L);  // large
        registry.record(10000L); // oversized

        adminClient().get().uri("/admin/response-header-size-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("total")).longValue()).isEqualTo(5L);
                    assertThat(((Number) body.get("maxSeenBytes")).longValue()).isEqualTo(10000L);
                    Map<String, Object> buckets = (Map<String, Object>) body.get("buckets");
                    assertThat(((Number) buckets.get("small")).longValue()).isEqualTo(2L);
                    assertThat(((Number) buckets.get("medium")).longValue()).isEqualTo(1L);
                    assertThat(((Number) buckets.get("large")).longValue()).isEqualTo(1L);
                    assertThat(((Number) buckets.get("oversized")).longValue()).isEqualTo(1L);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void resetEndpoint_clearsCounters() {
        registry.record(256L);

        adminClient().post().uri("/admin/response-header-size-stats/reset")
                .exchange()
                .expectStatus().isOk();

        assertThat(registry.getTotal()).isZero();
        assertThat(registry.getMaxSeen()).isZero();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getStats_userRole_returns403() {
        userClient().get().uri("/admin/response-header-size-stats")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void classify_coversAllBucketBoundaries() {
        assertThat(ResponseHeaderSizeRegistry.classify(0L)).isEqualTo("small");
        assertThat(ResponseHeaderSizeRegistry.classify(512L)).isEqualTo("small");
        assertThat(ResponseHeaderSizeRegistry.classify(513L)).isEqualTo("medium");
        assertThat(ResponseHeaderSizeRegistry.classify(2048L)).isEqualTo("medium");
        assertThat(ResponseHeaderSizeRegistry.classify(2049L)).isEqualTo("large");
        assertThat(ResponseHeaderSizeRegistry.classify(8192L)).isEqualTo("large");
        assertThat(ResponseHeaderSizeRegistry.classify(8193L)).isEqualTo("oversized");
        assertThat(ResponseHeaderSizeRegistry.classify(100000L)).isEqualTo("oversized");
    }
}
