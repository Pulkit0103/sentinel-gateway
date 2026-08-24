package com.sentinelgateway.gateway.routeblock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Integration tests for {@link RouteBlockFilter} and {@link RouteBlockController}
 * (Phase 20: Admin Route Blocking).
 *
 * Scenarios:
 *   1. Route not blocked → 200 passthrough
 *   2. Route blocked → 503 with JSON body
 *   3. Block via controller → subsequent request gets 503
 *   4. Unblock via controller → subsequent request gets 200
 *   5. 503 body contains routeId and error fields
 *   6. Block controller requires ADMIN role
 *   7. List blocked routes shows blocked route IDs
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RouteBlockFilterTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteBlockRegistry registry;

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

        reg.add("sentinel.gateway.routes[0].route-id",    () -> "blockable-route");
        reg.add("sentinel.gateway.routes[0].path",        () -> "/api/blockable/**");
        reg.add("sentinel.gateway.routes[0].service-uri", () -> base);
        reg.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        reg.add("sentinel.gateway.routes[0].enabled",     () -> "true");
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
        wireMock.stubFor(get(urlPathMatching("/api/blockable/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));
    }

    @AfterEach
    void resetRegistry() {
        registry.unblockAll();
    }

    private WebTestClient authed() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void routeNotBlocked_returns200() {
        authed().get().uri("/api/blockable/resource")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void routeBlocked_returns503() {
        registry.block("blockable-route");

        authed().get().uri("/api/blockable/resource")
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void blockViaController_thenRequest_returns503() {
        adminClient().post().uri("/admin/route-blocks/blockable-route")
                .exchange()
                .expectStatus().isOk();

        authed().get().uri("/api/blockable/resource")
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void unblockViaController_thenRequest_returns200() {
        registry.block("blockable-route");

        adminClient().delete().uri("/admin/route-blocks/blockable-route")
                .exchange()
                .expectStatus().isOk();

        authed().get().uri("/api/blockable/resource")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void blockedRoute_503Body_hasExpectedFields() {
        registry.block("blockable-route");

        authed().get().uri("/api/blockable/resource")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body.get("error")).isEqualTo("Route temporarily blocked");
                    assertThat(body.get("routeId")).isEqualTo("blockable-route");
                    assertThat(((Number) body.get("status")).intValue()).isEqualTo(503);
                });
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void blockController_requiresAdminRole_returns403ForUser() {
        authed().post().uri("/admin/route-blocks/blockable-route")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 7 ──────────────────────────────────────────────────────────────────

    @Test
    void listBlockedRoutes_showsBlockedIds() {
        registry.block("blockable-route");

        adminClient().get().uri("/admin/route-blocks")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKey("blockedRoutes");
                    assertThat(body).containsKey("count");
                });
    }
}
