package com.sentinelgateway.gateway.ipfilter;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
 * Integration tests for Phase 7: Per-Route IP Denylist.
 *
 * Route config: blockedIps=192.168.100.1, no allowlist.
 *
 * Tests:
 *   1. blockedIp_returns403  — X-Forwarded-For: 192.168.100.1 → 403 Forbidden
 *   2. nonBlockedIp_returns200 — X-Forwarded-For: 10.0.0.1 (not blocked) → 200
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class IpFilterBlocklistTest {

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

        String base = "http://localhost:" + wireMock.port();

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "");

        // Route with a blocked IP; no allowlist
        registry.add("sentinel.gateway.routes[0].route-id",    () -> "ip-blocklist-test");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/ip-blocklist/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
        registry.add("sentinel.gateway.routes[0].blocked-ips", () -> "192.168.100.1");

        // Ensure IP filter is enabled
        registry.add("sentinel.ip-filter.enabled", () -> "true");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();

        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));

        wireMock.stubFor(get(urlPathMatching("/api/ip-blocklist/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));
    }

    private WebTestClient authed() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    /**
     * A request from the explicitly blocked IP must receive 403 Forbidden.
     * The blocked IP is injected via X-Forwarded-For since the test client's
     * actual remote address is loopback (127.0.0.1).
     */
    @Test
    void blockedIp_returns403() {
        authed().get().uri("/api/ip-blocklist/resource")
                .header("X-Forwarded-For", "192.168.100.1")
                .exchange()
                .expectStatus().isForbidden();
    }

    /**
     * A request from an IP that is NOT in the blocklist should pass through normally (200).
     */
    @Test
    void nonBlockedIp_returns200() {
        authed().get().uri("/api/ip-blocklist/resource")
                .header("X-Forwarded-For", "10.0.0.1")
                .exchange()
                .expectStatus().isOk();
    }
}
