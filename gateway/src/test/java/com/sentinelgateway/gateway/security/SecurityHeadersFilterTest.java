package com.sentinelgateway.gateway.security;

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

import java.util.LinkedHashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for {@link SecurityHeadersFilter} (Phase 29: Response Security Headers).
 *
 * Scenarios:
 *   1. Default config overrides Referrer-Policy to strict-origin-when-cross-origin
 *   2. Default config adds Permissions-Policy (not set by Spring Security)
 *   3. Filter disabled — Permissions-Policy absent (Spring Security does not add it)
 *   4. Custom header appears in response
 *   5. Blank-value removes Permissions-Policy from response
 *   6. GET /admin/security-headers returns config (ROLE_ADMIN)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SecurityHeadersFilterTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private SecurityHeadersProperties properties;

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
        properties.setEnabled(true);
        properties.setHeaders(new LinkedHashMap<>(Map.of(
                "X-Content-Type-Options", "nosniff",
                "X-Frame-Options", "DENY",
                "Referrer-Policy", "strict-origin-when-cross-origin",
                "X-XSS-Protection", "0",
                "Permissions-Policy", "interest-cohort=()"
        )));
    }

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void defaultHeaders_referrerPolicyOverridesSpringSecurityValue() {
        // Spring Security writes Referrer-Policy: no-referrer; our filter overwrites with strict-origin-when-cross-origin
        adminClient().get().uri("/admin/security-headers")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin");
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void defaultHeaders_permissionsPolicyPresent() {
        // Permissions-Policy is not added by Spring Security; only our filter adds it
        adminClient().get().uri("/admin/security-headers")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Permissions-Policy", "interest-cohort=()");
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void filterDisabled_permissionsPolicyAbsent() {
        properties.setEnabled(false);

        // Spring Security does not set Permissions-Policy; disabled filter adds nothing → absent
        adminClient().get().uri("/admin/security-headers")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("Permissions-Policy");
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void customHeader_appearsInResponse() {
        Map<String, String> custom = new LinkedHashMap<>(properties.getHeaders());
        custom.put("Cross-Origin-Opener-Policy", "same-origin");
        properties.setHeaders(custom);

        adminClient().get().uri("/admin/security-headers")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Cross-Origin-Opener-Policy", "same-origin");
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void blankValueOverride_permissionsPolicyRemoved() {
        Map<String, String> custom = new LinkedHashMap<>(properties.getHeaders());
        custom.put("Permissions-Policy", "");
        properties.setHeaders(custom);

        adminClient().get().uri("/admin/security-headers")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("Permissions-Policy");
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void getConfig_admin_returnsExpectedFields() {
        adminClient().get().uri("/admin/security-headers")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").isEqualTo(true)
                .jsonPath("$.headers").exists()
                .jsonPath("$.headers['X-Content-Type-Options']").isEqualTo("nosniff");
    }
}
