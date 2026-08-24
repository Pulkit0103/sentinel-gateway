package com.sentinelgateway.gateway.cachettl;

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
 * Integration + unit tests for Cache-Control TTL distribution (Phase 57).
 *
 * Scenarios:
 *   1. GET /admin/cache-ttl-stats returns expected fields
 *   2. Seeded Cache-Control values are bucketed correctly
 *   3. POST /admin/cache-ttl-stats/reset clears counters
 *   4. Requires ROLE_ADMIN
 *   5. CacheTtlRegistry.classify covers all buckets
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CacheTtlControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CacheTtlRegistry registry;

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
        adminClient().get().uri("/admin/cache-ttl-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("enabled", "total", "buckets");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void seededValues_bucketedCorrectly() {
        registry.record("no-cache");          // → no-cache
        registry.record("no-store");          // → no-cache
        registry.record(null);               // → no-cache
        registry.record("max-age=30");       // → short
        registry.record("max-age=600");      // → medium
        registry.record("max-age=86400");    // → long

        adminClient().get().uri("/admin/cache-ttl-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("total")).longValue()).isEqualTo(6L);
                    Map<String, Object> buckets = (Map<String, Object>) body.get("buckets");
                    assertThat(((Number) buckets.get("no-cache")).longValue()).isEqualTo(3L);
                    assertThat(((Number) buckets.get("short")).longValue()).isEqualTo(1L);
                    assertThat(((Number) buckets.get("medium")).longValue()).isEqualTo(1L);
                    assertThat(((Number) buckets.get("long")).longValue()).isEqualTo(1L);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void resetEndpoint_clearsCounters() {
        registry.record("max-age=60");

        adminClient().post().uri("/admin/cache-ttl-stats/reset")
                .exchange()
                .expectStatus().isOk();

        assertThat(registry.getTotal()).isZero();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getStats_userRole_returns403() {
        userClient().get().uri("/admin/cache-ttl-stats")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void classify_coversAllBuckets() {
        assertThat(CacheTtlRegistry.classify(null)).isEqualTo("no-cache");
        assertThat(CacheTtlRegistry.classify("no-store, no-cache")).isEqualTo("no-cache");
        assertThat(CacheTtlRegistry.classify("max-age=0")).isEqualTo("no-cache");
        assertThat(CacheTtlRegistry.classify("max-age=1")).isEqualTo("short");
        assertThat(CacheTtlRegistry.classify("max-age=60")).isEqualTo("short");
        assertThat(CacheTtlRegistry.classify("max-age=61")).isEqualTo("medium");
        assertThat(CacheTtlRegistry.classify("max-age=3600")).isEqualTo("medium");
        assertThat(CacheTtlRegistry.classify("max-age=3601")).isEqualTo("long");
        assertThat(CacheTtlRegistry.classify("public, max-age=7200")).isEqualTo("long");
    }
}
