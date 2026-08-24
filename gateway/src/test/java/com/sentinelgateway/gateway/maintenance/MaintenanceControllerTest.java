package com.sentinelgateway.gateway.maintenance;

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
 * Integration tests for {@link MaintenanceController} (Phase 18: Maintenance Mode).
 *
 * Scenarios:
 *   GET  /admin/maintenance              → 200 with status body (ADMIN)
 *   POST /admin/maintenance/enable       → 200, enabled=true (ADMIN)
 *   POST /admin/maintenance/disable      → 200, enabled=false (ADMIN)
 *   POST /admin/maintenance/enable       → 401 (unauthenticated)
 *   POST /admin/maintenance/enable       → 403 (USER role)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class MaintenanceControllerTest {

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

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + wireMock.port() + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    @AfterEach
    void resetMaintenance() {
        maintenanceProperties.setEnabled(false);
    }

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private WebTestClient userClient() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void getStatus_admin_returns200WithBody() {
        adminClient().get().uri("/admin/maintenance")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKey("enabled");
                    assertThat(body).containsKey("message");
                    assertThat(body).containsKey("retryAfterSeconds");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void enable_admin_setsEnabledTrue() {
        adminClient().post().uri("/admin/maintenance/enable")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body.get("enabled")).isEqualTo(true));

        assertThat(maintenanceProperties.isEnabled()).isTrue();
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void disable_admin_setsEnabledFalse() {
        maintenanceProperties.setEnabled(true);

        adminClient().post().uri("/admin/maintenance/disable")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body.get("enabled")).isEqualTo(false));

        assertThat(maintenanceProperties.isEnabled()).isFalse();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void enable_unauthenticated_returns401() {
        webTestClient.post().uri("/admin/maintenance/enable")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void enable_userRole_returns403() {
        userClient().post().uri("/admin/maintenance/enable")
                .exchange()
                .expectStatus().isForbidden();
    }
}
