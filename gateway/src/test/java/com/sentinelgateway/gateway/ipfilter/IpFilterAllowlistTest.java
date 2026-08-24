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
 * Integration tests for Phase 7: Per-Route IP Allowlist.
 *
 * Route config: allowedIps=10.0.0.0/8, no denylist.
 *
 * Tests:
 *   1. allowedCidr_returns200    — X-Forwarded-For: 10.5.5.5 (in 10.0.0.0/8) → 200
 *   2. nonAllowedIp_returns403   — X-Forwarded-For: 172.16.0.1 (outside 10.0.0.0/8) → 403
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class IpFilterAllowlistTest {

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

        // Route with an allowlist of 10.0.0.0/8 only; no denylist
        registry.add("sentinel.gateway.routes[0].route-id",    () -> "ip-allowlist-test");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/ip-allowlist/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
        registry.add("sentinel.gateway.routes[0].allowed-ips", () -> "10.0.0.0/8");

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

        wireMock.stubFor(get(urlPathMatching("/api/ip-allowlist/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));
    }

    private WebTestClient authed() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    /**
     * A request from an IP within the allowed CIDR range (10.0.0.0/8) must pass through.
     */
    @Test
    void allowedCidr_returns200() {
        authed().get().uri("/api/ip-allowlist/resource")
                .header("X-Forwarded-For", "10.5.5.5")
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * A request from an IP outside the allowlist must be blocked with 403 Forbidden.
     */
    @Test
    void nonAllowedIp_returns403() {
        authed().get().uri("/api/ip-allowlist/resource")
                .header("X-Forwarded-For", "172.16.0.1")
                .exchange()
                .expectStatus().isForbidden();
    }
}
