package com.sentinelgateway.gateway.routing;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Phase 16: Dynamic Route Reload.
 *
 * Verifies that:
 * 1. {@code POST /admin/routes/refresh} returns 204 and is secured (ADMIN only).
 * 2. {@link RouteService} publishes a {@link RefreshRoutesEvent} whenever a route is saved.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@RecordApplicationEvents
class DynamicRouteReloadTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    ApplicationEvents applicationEvents;

    @Autowired
    private RouteService routeService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + wireMock.port() + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private WebTestClient userClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // ── Test 1: Manual refresh endpoint ──────────────────────────────────────

    @Test
    void manualRefresh_returns204() {
        adminClient().post().uri("/admin/routes/refresh")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void manualRefresh_nonAdmin_returns403() {
        userClient().post().uri("/admin/routes/refresh")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void manualRefresh_unauthenticated_returns401() {
        webTestClient.post().uri("/admin/routes/refresh")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── Test 2: RouteService publishes RefreshRoutesEvent on save ─────────────

    @Test
    void routeService_publishesRefreshEvent_onSave() {
        String routeId = "refresh-event-test-" + System.currentTimeMillis();
        RouteDefinition definition = new RouteDefinition(
                routeId,
                "/api/refresh-test/**",
                "http://localhost:" + wireMock.port(),
                java.util.List.of("GET"),
                true,
                java.util.List.of(),
                false,
                "DEFAULT"
        );

        // Create the route — RouteService.create() calls refresh() which publishes the event
        routeService.create(definition).block();

        long count = applicationEvents.stream(RefreshRoutesEvent.class).count();
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    // ── Test 3: Refresh endpoint idempotency (callable multiple times) ──────────

    @Test
    void manualRefresh_isIdempotent() {
        // Two back-to-back refreshes should both succeed
        adminClient().post().uri("/admin/routes/refresh").exchange().expectStatus().isNoContent();
        adminClient().post().uri("/admin/routes/refresh").exchange().expectStatus().isNoContent();
    }
}
