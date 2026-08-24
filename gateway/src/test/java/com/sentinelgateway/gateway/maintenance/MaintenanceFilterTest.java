package com.sentinelgateway.gateway.maintenance;

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
 * Integration tests for {@link MaintenanceFilter} (Phase 18: Maintenance Mode).
 *
 * {@link MaintenanceProperties} is injected and toggled directly between tests so
 * a single Spring context covers both enabled and disabled scenarios.
 *
 * Scenarios:
 *   1. Maintenance disabled → normal 200 passthrough
 *   2. Maintenance enabled → anonymous request → 503 with JSON body
 *   3. Maintenance enabled → ADMIN role → 200 bypass
 *   4. Maintenance enabled → 503 body contains expected JSON fields
 *   5. Maintenance enabled → 503 includes Retry-After header
 *   6. Maintenance enabled → /admin/** still reachable (controller itself is exempt)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class MaintenanceFilterTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MaintenanceProperties maintenanceProperties;

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

        registry.add("sentinel.gateway.routes[0].route-id",    () -> "maintenance-test");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/maintenance-check/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));
        wireMock.stubFor(get(urlPathMatching("/api/maintenance-check/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));
    }

    @AfterEach
    void resetMaintenance() {
        maintenanceProperties.setEnabled(false);
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
    void maintenanceDisabled_requestPassesThrough() {
        maintenanceProperties.setEnabled(false);

        authed().get().uri("/api/maintenance-check/ping")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void maintenanceEnabled_regularUser_returns503() {
        maintenanceProperties.setEnabled(true);

        authed().get().uri("/api/maintenance-check/ping")
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void maintenanceEnabled_adminRole_bypassesMaintenanceMode() {
        maintenanceProperties.setEnabled(true);

        adminClient().get().uri("/api/maintenance-check/ping")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void maintenanceEnabled_503Body_containsExpectedFields() {
        maintenanceProperties.setEnabled(true);

        authed().get().uri("/api/maintenance-check/ping")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKey("status");
                    assertThat(body.get("status")).isEqualTo("maintenance");
                    assertThat(body).containsKey("message");
                    assertThat(body).containsKey("retryAfterSeconds");
                });
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void maintenanceEnabled_503_hasRetryAfterHeader() {
        maintenanceProperties.setEnabled(true);

        authed().get().uri("/api/maintenance-check/ping")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().exists("Retry-After");
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void maintenanceEnabled_adminEndpoint_isStillReachable() {
        maintenanceProperties.setEnabled(true);

        adminClient().get().uri("/admin/maintenance")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body.get("enabled")).isEqualTo(true));
    }
}
