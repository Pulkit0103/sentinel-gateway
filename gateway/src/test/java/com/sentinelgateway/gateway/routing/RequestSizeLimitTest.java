package com.sentinelgateway.gateway.routing;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for Phase 8: Per-Route Request Size Limiting.
 *
 * A route is configured with {@code max-body-bytes=100}. The gateway wires a
 * Spring Cloud Gateway {@code RequestSize} filter that rejects bodies exceeding
 * 100 bytes with HTTP 413 Payload Too Large.
 *
 * Tests:
 *   1. smallBody_returns200  — body ≤ 100 bytes passes through → 200
 *   2. largeBody_returns413  — body > 100 bytes is rejected     → 413
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RequestSizeLimitTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));

        wireMock.stubFor(post(urlPathMatching("/api/size/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        String base = "http://localhost:" + wireMock.port();

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "");

        // Route with a 100-byte request size limit
        registry.add("sentinel.gateway.routes[0].route-id",       () -> "size-limit-test");
        registry.add("sentinel.gateway.routes[0].path",           () -> "/api/size/**");
        registry.add("sentinel.gateway.routes[0].service-uri",    () -> base);
        registry.add("sentinel.gateway.routes[0].methods",        () -> "POST");
        registry.add("sentinel.gateway.routes[0].enabled",        () -> "true");
        registry.add("sentinel.gateway.routes[0].max-body-bytes", () -> "100");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    private WebTestClient authed() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    /**
     * A request body well within the 100-byte limit must be proxied to the
     * upstream and return 200 OK.
     */
    @Test
    void smallBody_withinLimit_returns200() {
        // 50-character body — well under the 100-byte limit
        String smallBody = "A".repeat(50);

        authed().post().uri("/api/size/resource")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(smallBody)
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * A request body exceeding the 100-byte limit must be rejected by the gateway
     * with HTTP 413 Payload Too Large before it reaches the upstream.
     */
    @Test
    void largeBody_exceedsLimit_returns413() {
        // 200-character body — double the 100-byte limit
        String largeBody = "B".repeat(200);

        authed().post().uri("/api/size/resource")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(largeBody)
                .exchange()
                .expectStatus().isEqualTo(413);
    }
}
