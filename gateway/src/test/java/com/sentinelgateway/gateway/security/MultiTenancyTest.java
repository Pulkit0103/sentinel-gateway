package com.sentinelgateway.gateway.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sentinelgateway.gateway.apikey.ApiKeyService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Multi-tenancy integration tests.
 *
 * Route setup:
 *   /api/tenant-data/** — tenant-required: true, requires USER_READ
 *   /api/open/**        — tenant-required: false, requires USER_READ
 *
 * Test matrix:
 *   JWT with tenant + tenant-required route     → 200  (own tenant allowed)
 *   JWT without tenant + tenant-required route  → 403  (missing tenant)
 *   API key with tenant + tenant-required route → 200  (API key tenant allowed)
 *   API key without tenant + tenant-required    → 403  (missing tenant)
 *   JWT with tenant + non-tenant-required route → 200  (no restriction)
 *   Caller-supplied X-Tenant-Id spoofing        → 403  (spoofed header rejected)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class MultiTenancyTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String TEST_ISSUER = "http://test-tenant-issuer/realms/sentinel";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ApiKeyService apiKeyService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("tenant-test-key").generate();
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
        registry.add("sentinel.security.jwt.issuer", () -> TEST_ISSUER);

        // Tenant-required route
        registry.add("sentinel.gateway.routes[0].route-id",             () -> "tenant-data");
        registry.add("sentinel.gateway.routes[0].path",                 () -> "/api/tenant-data/**");
        registry.add("sentinel.gateway.routes[0].service-uri",          () -> base);
        registry.add("sentinel.gateway.routes[0].methods",              () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",              () -> "true");
        registry.add("sentinel.gateway.routes[0].required-scopes[0]",  () -> "USER_READ");
        registry.add("sentinel.gateway.routes[0].tenant-required",      () -> "true");

        // Non-tenant-required route
        registry.add("sentinel.gateway.routes[1].route-id",             () -> "open-data");
        registry.add("sentinel.gateway.routes[1].path",                 () -> "/api/open/**");
        registry.add("sentinel.gateway.routes[1].service-uri",          () -> base);
        registry.add("sentinel.gateway.routes[1].methods",              () -> "GET");
        registry.add("sentinel.gateway.routes[1].enabled",              () -> "true");
        registry.add("sentinel.gateway.routes[1].required-scopes[0]",  () -> "USER_READ");
        registry.add("sentinel.gateway.routes[1].tenant-required",      () -> "false");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String token(String tenantId, String... roles) throws Exception {
        var signer = new RSASSASigner(rsaKey);
        var claims = new JWTClaimsSet.Builder()
                .subject("user-" + UUID.randomUUID())
                .issuer(TEST_ISSUER)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("realm_access", Map.of("roles", List.of(roles)));

        if (tenantId != null) {
            claims.claim("tenant_id", tenantId);
        }

        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims.build());
        jwt.sign(signer);
        return jwt.serialize();
    }

    // ── JWT with tenant ───────────────────────────────────────────────────────

    @Test
    void jwtWithTenant_tenantRequired_returns200() throws Exception {
        webTestClient.get().uri("/api/tenant-data/resource")
                .header("Authorization", "Bearer " + token("tenant-acme", "USER"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void jwtWithoutTenant_tenantRequired_returns403() throws Exception {
        // No tenant_id claim in JWT
        webTestClient.get().uri("/api/tenant-data/resource")
                .header("Authorization", "Bearer " + token(null, "USER"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void jwtWithTenant_nonTenantRequired_returns200() throws Exception {
        // tenant-required: false — any authenticated user passes
        webTestClient.get().uri("/api/open/resource")
                .header("Authorization", "Bearer " + token("tenant-acme", "USER"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void jwtWithoutTenant_nonTenantRequired_returns200() throws Exception {
        webTestClient.get().uri("/api/open/resource")
                .header("Authorization", "Bearer " + token(null, "USER"))
                .exchange()
                .expectStatus().isOk();
    }

    // ── Tenant spoofing prevention ─────────────────────────────────────────────

    @Test
    void spoofedXTenantIdHeader_withoutCredential_returns401() {
        // Caller tries to spoof tenant identity with a raw header — no credential → 401
        webTestClient.get().uri("/api/tenant-data/resource")
                .header("X-Tenant-Id", "tenant-evil")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void spoofedXTenantIdHeader_jwtWithoutTenant_returns403() throws Exception {
        // JWT has no tenant_id claim; caller also sends X-Tenant-Id spoofing header
        // The gateway must not trust the raw header — tenant context must come from JWT only
        webTestClient.get().uri("/api/tenant-data/resource")
                .header("Authorization", "Bearer " + token(null, "USER"))
                .header("X-Tenant-Id", "tenant-evil")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── API key with tenant ────────────────────────────────────────────────────

    @Test
    void apiKeyWithTenant_tenantRequired_returns200() throws Exception {
        ApiKeyService.CreatedApiKey created = apiKeyService
                .create("client-tenant", "tenant-acme", "USER_READ", null)
                .block();

        webTestClient.get().uri("/api/tenant-data/resource")
                .header("X-API-Key", created.rawKey())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void apiKeyWithoutTenant_tenantRequired_returns403() throws Exception {
        ApiKeyService.CreatedApiKey created = apiKeyService
                .create("client-notenant", null, "USER_READ", null)
                .block();

        webTestClient.get().uri("/api/tenant-data/resource")
                .header("X-API-Key", created.rawKey())
                .exchange()
                .expectStatus().isForbidden();
    }
}
