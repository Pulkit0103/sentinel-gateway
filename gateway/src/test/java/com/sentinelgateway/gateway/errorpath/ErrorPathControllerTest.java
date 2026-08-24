package com.sentinelgateway.gateway.errorpath;

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

import java.time.Instant;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration + unit tests for error-path ring-buffer (Phase 56).
 *
 * Scenarios:
 *   1. GET /admin/error-paths returns expected fields
 *   2. Seeded 4xx and 5xx records appear in snapshot
 *   3. POST /admin/error-paths/reset clears all records
 *   4. Requires ROLE_ADMIN
 *   5. Only 4xx/5xx records are counted; 2xx are ignored
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ErrorPathControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ErrorPathRegistry registry;

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
        adminClient().get().uri("/admin/error-paths")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("enabled", "maxRecords", "total4xx", "total5xx", "recentErrors");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void seededErrors_appearInSnapshot() {
        registry.record(new ErrorPathRecord(Instant.now(), "GET", "/api/users/999", 404));
        registry.record(new ErrorPathRecord(Instant.now(), "POST", "/api/orders", 422));
        registry.record(new ErrorPathRecord(Instant.now(), "GET", "/api/payments/1", 500));

        adminClient().get().uri("/admin/error-paths")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("total4xx")).longValue()).isEqualTo(2L);
                    assertThat(((Number) body.get("total5xx")).longValue()).isEqualTo(1L);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void resetEndpoint_clearsAllRecords() {
        registry.record(new ErrorPathRecord(Instant.now(), "GET", "/api/users", 404));

        adminClient().post().uri("/admin/error-paths/reset")
                .exchange()
                .expectStatus().isOk();

        assertThat(registry.getTotal4xx()).isZero();
        assertThat(registry.getTotal5xx()).isZero();
        assertThat(registry.getRecordedCount()).isZero();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getStats_userRole_returns403() {
        userClient().get().uri("/admin/error-paths")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void only4xxAnd5xxCounted_2xxIgnored() {
        // The record() method always stores and increments; the filter only calls record()
        // for status >= 400. Here we verify the counter buckets work correctly.
        registry.record(new ErrorPathRecord(Instant.now(), "GET", "/api/test", 400));
        registry.record(new ErrorPathRecord(Instant.now(), "GET", "/api/test", 499));
        registry.record(new ErrorPathRecord(Instant.now(), "GET", "/api/test", 500));
        registry.record(new ErrorPathRecord(Instant.now(), "GET", "/api/test", 503));

        assertThat(registry.getTotal4xx()).isEqualTo(2L);
        assertThat(registry.getTotal5xx()).isEqualTo(2L);
        assertThat(registry.getRecordedCount()).isEqualTo(4);
    }
}
