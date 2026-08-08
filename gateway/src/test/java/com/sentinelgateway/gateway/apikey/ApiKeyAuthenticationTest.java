package com.sentinelgateway.gateway.apikey;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for X-API-Key header authentication.
 *
 * Scenarios:
 *   valid ACTIVE key                → 200
 *   REVOKED key                    → 401
 *   EXPIRED key (expiresAt in past) → 401
 *   unknown/wrong key              → 401
 *   no key header + no JWT         → 401 (Spring Security default)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApiKeyAuthenticationTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("api-key-test").generate();
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        JWKSet publicJwkSet = new JWKSet(rsaKey.toPublicJWK());
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(publicJwkSet.toString())));

        wireMock.stubFor(get(urlPathMatching("/api/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        String base = "http://localhost:" + wireMock.port();

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "http://test-api-key-issuer/realms/sentinel");

        registry.add("sentinel.gateway.routes[0].route-id",            () -> "user-service");
        registry.add("sentinel.gateway.routes[0].path",                () -> "/api/users/**");
        registry.add("sentinel.gateway.routes[0].service-uri",         () -> base);
        registry.add("sentinel.gateway.routes[0].methods",             () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",             () -> "true");
        registry.add("sentinel.gateway.routes[0].required-scopes[0]", () -> "USER_READ");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    // ── valid key ─────────────────────────────────────────────────────────────

    @Test
    void validActiveKey_returns200() throws Exception {
        ApiKeyService.CreatedApiKey created = apiKeyService
                .create("client-test", "tenant-acme", "USER_READ", null)
                .block();

        webTestClient.get().uri("/api/users/1")
                .header("X-API-Key", created.rawKey())
                .exchange()
                .expectStatus().isOk();
    }

    // ── revoked key ───────────────────────────────────────────────────────────

    @Test
    void revokedKey_returns401() throws Exception {
        ApiKeyService.CreatedApiKey created = apiKeyService
                .create("client-revoke", "tenant-acme", "USER_READ", null)
                .block();

        // Revoke the key
        apiKeyService.revoke(created.entity().getId()).block();

        webTestClient.get().uri("/api/users/1")
                .header("X-API-Key", created.rawKey())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── expired key ───────────────────────────────────────────────────────────

    @Test
    void expiredKey_returns401() throws Exception {
        // Create a key that expired 1 hour ago
        ApiKeyService.CreatedApiKey created = apiKeyService
                .create("client-expired", "tenant-acme", "USER_READ",
                        Instant.now().minusSeconds(3600))
                .block();

        webTestClient.get().uri("/api/users/1")
                .header("X-API-Key", created.rawKey())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── wrong key ─────────────────────────────────────────────────────────────

    @Test
    void wrongKey_returns401() {
        webTestClient.get().uri("/api/users/1")
                .header("X-API-Key", "sgk_thisisnotthecorrectkey")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── no credentials ────────────────────────────────────────────────────────

    @Test
    void noCredentials_returns401() {
        webTestClient.get().uri("/api/users/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── scope enforcement with API key ────────────────────────────────────────

    @Test
    void apiKeyWithoutRequiredScope_returns403() throws Exception {
        // Key has no scopes → cannot access USER_READ-guarded route
        ApiKeyService.CreatedApiKey created = apiKeyService
                .create("client-noscope", "tenant-acme", "", null)
                .block();

        webTestClient.get().uri("/api/users/1")
                .header("X-API-Key", created.rawKey())
                .exchange()
                .expectStatus().isForbidden();
    }
}
