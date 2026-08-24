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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for {@link JwtExpiryFilter} (Phase 36: JWT Expiry Warning).
 *
 * Scenarios:
 *   1. JWT expiring within window → X-JWT-Expires-In header added
 *   2. JWT expiring outside window → header absent
 *   3. Filter disabled → no header even for soon-expiring JWT
 *   4. No JWT (unauthenticated) → no header
 *   5. Custom header name reflected in response
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class JwtExpiryFilterTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtExpiryProperties properties;

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
        properties.setWarningWindowSeconds(300);
        properties.setHeaderName("X-JWT-Expires-In");
    }

    /** Create a mock JWT with specific exp claim. */
    private SecurityMockServerConfigurers.JwtMutator jwtWithExpiry(Instant expiresAt) {
        return SecurityMockServerConfigurers.mockJwt()
                .jwt(jwt -> jwt.expiresAt(expiresAt)
                        .issuedAt(Instant.now().minusSeconds(60)));
    }

    private WebTestClient adminClient(Instant expiresAt) {
        return webTestClient.mutateWith(
                jwtWithExpiry(expiresAt)
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void jwtExpiringSoon_headerAdded() {
        // JWT expires in 60 seconds, window is 300s → should add header
        Instant expiresAt = Instant.now().plusSeconds(60);

        adminClient(expiresAt).get().uri("/admin/security-headers")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-JWT-Expires-In");
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void jwtExpiringLater_headerAbsent() {
        // JWT expires in 3600 seconds, window is 300s → no header
        Instant expiresAt = Instant.now().plusSeconds(3600);

        adminClient(expiresAt).get().uri("/admin/security-headers")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("X-JWT-Expires-In");
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void filterDisabled_noHeaderEvenForSoonExpiry() {
        properties.setEnabled(false);
        Instant expiresAt = Instant.now().plusSeconds(30);

        adminClient(expiresAt).get().uri("/admin/security-headers")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("X-JWT-Expires-In");
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void customHeaderName_appearsInResponse() {
        properties.setHeaderName("X-Token-Expires-Soon");
        Instant expiresAt = Instant.now().plusSeconds(60);

        adminClient(expiresAt).get().uri("/admin/security-headers")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Token-Expires-Soon");
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void jwtAtExactWindowBoundary_headerAdded() {
        // JWT expires in exactly warningWindowSeconds → should add header (≤)
        Instant expiresAt = Instant.now().plusSeconds(300);

        adminClient(expiresAt).get().uri("/admin/security-headers")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-JWT-Expires-In");
    }
}
