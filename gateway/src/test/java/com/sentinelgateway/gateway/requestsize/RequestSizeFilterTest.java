package com.sentinelgateway.gateway.requestsize;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RequestSizeFilter} (Phase 37: Request Size Guard).
 *
 * Scenarios:
 *   1. Request within size limit → 200 OK
 *   2. Request exceeding size limit (via Content-Length) → 413
 *   3. Oversized request recorded in registry
 *   4. Filter disabled → large Content-Length passes through
 *   5. GET request without Content-Length → 200 OK (no Content-Length = no check)
 *   6. Admin endpoint returns correct fields
 *   7. Admin clear endpoint empties registry
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RequestSizeFilterTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RequestSizeProperties properties;

    @Autowired
    private OversizedRequestRegistry registry;

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
        properties.setMaxBodyBytes(10_485_760L);
        registry.clear();
    }

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void requestWithinLimit_passes() {
        adminClient().post().uri("/admin/request-sizes/clear")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void requestExceedingLimit_returns413() {
        // Set limit below the actual body size so Content-Length (set by HTTP client) exceeds it
        properties.setMaxBodyBytes(5L);
        String bigBody = "hello world this body is definitely over five bytes";

        webTestClient.post().uri("/admin/request-sizes/clear")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bigBody)
                .exchange()
                .expectStatus().isEqualTo(413);
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void oversizedRequest_recordedInRegistry() {
        properties.setMaxBodyBytes(5L);
        String bigBody = "hello world this body is definitely over five bytes";

        webTestClient.post().uri("/admin/request-sizes/clear")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bigBody)
                .exchange()
                .expectStatus().isEqualTo(413);

        assertThat(registry.count()).isEqualTo(1);
        assertThat(registry.snapshot().get(0).claimedBytes()).isGreaterThan(5L);
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void filterDisabled_largeBodyPasses() {
        properties.setEnabled(false);
        properties.setMaxBodyBytes(5L);
        String bigBody = "hello world this body is definitely over five bytes";

        adminClient().post().uri("/admin/request-sizes/clear")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bigBody)
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void getWithoutContentLength_passes() {
        // GET requests typically have no Content-Length; filter must not reject them
        adminClient().get().uri("/admin/request-sizes")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void adminEndpoint_returnsExpectedFields() {
        adminClient().get().uri("/admin/request-sizes")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("enabled", "maxBodyBytes", "count", "records");
                });
    }

    // ── Test 7 ──────────────────────────────────────────────────────────────────

    @Test
    void clearEndpoint_emptiesRegistry() {
        properties.setMaxBodyBytes(5L);
        String bigBody = "hello world this body is definitely over five bytes";
        webTestClient.post().uri("/admin/request-sizes/clear")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bigBody)
                .exchange()
                .expectStatus().isEqualTo(413);

        adminClient().post().uri("/admin/request-sizes/clear")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("remaining")).intValue()).isZero();
                });

        assertThat(registry.count()).isZero();
    }
}
