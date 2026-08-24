package com.sentinelgateway.gateway.pathlength;

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
 * Integration tests for {@link PathLengthFilter} (Phase 43: Request Path Length Guard).
 *
 * Scenarios:
 *   1. Normal-length path → 200 OK
 *   2. Path exceeding limit → 414 URI Too Long
 *   3. Query string exceeding limit → 400 Bad Request
 *   4. Filter disabled → long path passes through
 *   5. Rejection recorded in registry
 *   6. Admin endpoint returns expected fields
 *   7. Admin clear endpoint
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PathLengthFilterTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PathLengthProperties properties;

    @Autowired
    private PathLengthRegistry registry;

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
    void reset() {
        properties.setEnabled(true);
        properties.setMaxPathLength(2048);
        properties.setMaxQueryLength(4096);
        registry.clear();
    }

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void normalPath_passes() {
        adminClient().get().uri("/admin/path-rejections")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void pathExceedingLimit_returns414() {
        properties.setMaxPathLength(10);
        String longPath = "/admin/path-rejections/this/is/a/very/long/path";

        webTestClient.get().uri(longPath)
                .exchange()
                .expectStatus().isEqualTo(414);
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void queryExceedingLimit_returns400() {
        properties.setMaxQueryLength(5);

        webTestClient.get().uri("/admin/path-rejections?foo=bar&baz=qux&extra=value")
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void filterDisabled_longPathPasses() {
        properties.setEnabled(false);
        properties.setMaxPathLength(10);
        String longPath = "/admin/path-rejections/this/is/a/very/long/path";

        adminClient().get().uri(longPath)
                .exchange()
                .expectStatus().isNotFound(); // 404 - no route, but not 414
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void rejection_recordedInRegistry() {
        properties.setMaxPathLength(10);
        String longPath = "/admin/path-rejections/this/is/a/very/long/path";

        webTestClient.get().uri(longPath)
                .exchange()
                .expectStatus().isEqualTo(414);

        assertThat(registry.count()).isEqualTo(1);
        assertThat(registry.snapshot().get(0).violation()).isEqualTo("path");
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void adminEndpoint_returnsExpectedFields() {
        adminClient().get().uri("/admin/path-rejections")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys(
                            "enabled", "maxPathLength", "maxQueryLength", "count", "records");
                });
    }

    // ── Test 7 ──────────────────────────────────────────────────────────────────

    @Test
    void clearEndpoint_emptiesRegistry() {
        // maxPathLength=50; trigger path is 60 chars; clear path is 27 chars → clears OK
        properties.setMaxPathLength(50);
        String longPath = "/api/this-path-is-definitely-longer-than-fifty-chars-limit";
        webTestClient.get().uri(longPath)
                .exchange()
                .expectStatus().isEqualTo(414);

        adminClient().post().uri("/admin/path-rejections/clear")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("remaining")).intValue()).isZero();
                });

        assertThat(registry.count()).isZero();
    }
}
