package com.sentinelgateway.gateway.sanitizer;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for {@link RequestSanitizerFilter} (Phase 32: Request Header Sanitizer).
 *
 * Scenarios:
 *   1. Normal request passes
 *   2. Header value exceeding maxHeaderValueLength → 400
 *   3. Small limit — short header still passes
 *   4. Null byte in header value → 400
 *   5. Null byte check disabled — null byte passes
 *   6. Filter disabled — oversized header passes
 *   7. GET /admin/request-sanitizer returns config (ROLE_ADMIN)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RequestSanitizerFilterTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RequestSanitizerProperties properties;

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
    void resetProperties() {
        properties.setEnabled(true);
        properties.setMaxHeaderValueLength(8192);
        properties.setBlockNullBytes(true);
    }

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void normalRequest_passes() {
        adminClient().get().uri("/admin/request-sanitizer")
                .header("X-Custom-Header", "normal-value")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void oversizedHeader_returns400() {
        properties.setMaxHeaderValueLength(50);
        String longValue = "x".repeat(51);

        adminClient().get().uri("/admin/request-sanitizer")
                .header("X-Oversized", longValue)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void headerBelowLimit_passes() {
        properties.setMaxHeaderValueLength(50);
        String shortValue = "x".repeat(20);

        adminClient().get().uri("/admin/request-sanitizer")
                .header("X-Short", shortValue)
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void nullByteInHeader_returns400() {
        adminClient().get().uri("/admin/request-sanitizer")
                .header("X-Malicious", "value\0injected")
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void nullByteCheckDisabled_passes() {
        properties.setBlockNullBytes(false);

        adminClient().get().uri("/admin/request-sanitizer")
                .header("X-Malicious", "value\0injected")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void filterDisabled_oversizedHeaderPasses() {
        properties.setEnabled(false);
        properties.setMaxHeaderValueLength(50);
        String longValue = "x".repeat(200);

        adminClient().get().uri("/admin/request-sanitizer")
                .header("X-Oversized", longValue)
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 7 ──────────────────────────────────────────────────────────────────

    @Test
    void getConfig_admin_returnsExpectedFields() {
        adminClient().get().uri("/admin/request-sanitizer")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").isEqualTo(true)
                .jsonPath("$.maxHeaderValueLength").isEqualTo(8192)
                .jsonPath("$.blockNullBytes").isEqualTo(true);
    }
}
