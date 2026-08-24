package com.sentinelgateway.gateway.ipaccess;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for {@link IpAccessFilter} (Phase 28: IP-Based Access Control).
 *
 * Scenarios:
 *   1. Filter disabled → all requests pass
 *   2. Denylist mode: IP in list → 403
 *   3. Denylist mode: IP not in list → 200
 *   4. Allowlist mode: IP in list → 200
 *   5. Allowlist mode: IP not in list → 403
 *   6. X-Forwarded-For takes precedence over remote address
 *   7. GET /admin/ip-access returns config (ROLE_ADMIN)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class IpAccessFilterTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private IpAccessProperties properties;

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
    void resetProperties() {
        properties.setEnabled(false);
        properties.setMode(IpAccessProperties.Mode.DENYLIST);
        properties.setGlobalList(new java.util.ArrayList<>());
        properties.setRoutes(new java.util.HashMap<>());
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
    void filterDisabled_requestPasses() {
        properties.setEnabled(false);

        adminClient().get().uri("/admin/ip-access")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void denylist_ipInList_returns403() {
        properties.setEnabled(true);
        properties.setMode(IpAccessProperties.Mode.DENYLIST);
        properties.setGlobalList(List.of("9.9.9.9"));

        // Spoof a blocked IP via X-Forwarded-For
        adminClient().get().uri("/admin/ip-access")
                .header("X-Forwarded-For", "9.9.9.9")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void denylist_ipNotInList_passes() {
        properties.setEnabled(true);
        properties.setMode(IpAccessProperties.Mode.DENYLIST);
        properties.setGlobalList(List.of("9.9.9.9"));

        adminClient().get().uri("/admin/ip-access")
                .header("X-Forwarded-For", "1.2.3.4")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void allowlist_ipInList_passes() {
        properties.setEnabled(true);
        properties.setMode(IpAccessProperties.Mode.ALLOWLIST);
        properties.setGlobalList(List.of("1.2.3.4"));

        adminClient().get().uri("/admin/ip-access")
                .header("X-Forwarded-For", "1.2.3.4")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void allowlist_ipNotInList_returns403() {
        properties.setEnabled(true);
        properties.setMode(IpAccessProperties.Mode.ALLOWLIST);
        properties.setGlobalList(List.of("1.2.3.4"));

        adminClient().get().uri("/admin/ip-access")
                .header("X-Forwarded-For", "5.5.5.5")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void xForwardedFor_firstEntryUsed() {
        properties.setEnabled(true);
        properties.setMode(IpAccessProperties.Mode.DENYLIST);
        properties.setGlobalList(List.of("9.9.9.9"));

        // First entry in X-Forwarded-For is the client IP
        adminClient().get().uri("/admin/ip-access")
                .header("X-Forwarded-For", "9.9.9.9, 10.0.0.1, 172.16.0.1")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 7 ──────────────────────────────────────────────────────────────────

    @Test
    void getConfig_admin_returnsExpectedFields() {
        adminClient().get().uri("/admin/ip-access")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").exists()
                .jsonPath("$.mode").exists()
                .jsonPath("$.globalList").exists()
                .jsonPath("$.routes").exists();
    }
}
