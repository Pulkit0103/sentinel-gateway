package com.sentinelgateway.gateway.routing;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for Phase 9: Per-Route Request Timeout.
 *
 * Two routes are configured:
 *   - "timeout-short": timeout-ms=500, upstream delays 2000ms → gateway returns 504
 *   - "timeout-long":  timeout-ms=5000, upstream responds immediately → gateway returns 200
 *
 * The per-route timeout is wired via route metadata (response-timeout / connect-timeout)
 * which is read by Spring Cloud Gateway's NettyRoutingFilter.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RouteTimeoutTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        // JWKS stub required for context startup
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));

        // Slow upstream: delays 2000ms — will exceed the 500ms timeout
        wireMock.stubFor(get(urlPathMatching("/api/slow/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay(2000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        // Fast upstream: responds immediately
        wireMock.stubFor(get(urlPathMatching("/api/fast/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        String base = "http://localhost:" + wireMock.port();

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "");

        // Route 0: short timeout (500ms) — upstream takes 2000ms → should time out
        registry.add("sentinel.gateway.routes[0].route-id",    () -> "timeout-short");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/slow/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
        registry.add("sentinel.gateway.routes[0].timeout-ms",  () -> "500");

        // Route 1: generous timeout (5000ms) — upstream responds immediately → should succeed
        registry.add("sentinel.gateway.routes[1].route-id",    () -> "timeout-long");
        registry.add("sentinel.gateway.routes[1].path",        () -> "/api/fast/**");
        registry.add("sentinel.gateway.routes[1].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[1].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[1].enabled",     () -> "true");
        registry.add("sentinel.gateway.routes[1].timeout-ms",  () -> "5000");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    private WebTestClient authed() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    /**
     * A route with a 500ms timeout against an upstream that delays 2000ms must
     * return 504 Gateway Timeout.
     */
    @Test
    void shortTimeout_slowUpstream_returns504() {
        authed().get().uri("/api/slow/resource")
                .exchange()
                .expectStatus().isEqualTo(504);
    }

    /**
     * A route with a 5000ms timeout against an upstream that responds immediately
     * must return 200 OK.
     */
    @Test
    void longTimeout_fastUpstream_returns200() {
        authed().get().uri("/api/fast/resource")
                .exchange()
                .expectStatus().isOk();
    }
}
