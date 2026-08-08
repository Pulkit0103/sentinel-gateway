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

import java.util.List;
import java.util.Map;
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
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for Phase 3: OAuth2/JWT authentication at the gateway layer.
 *
 * Uses a real RSA key pair and WireMock-served JWKS endpoint so every test
 * exercises the full JWT validation path (signature + timestamp + issuer).
 *
 * Acceptance criteria verified:
 *   1. No token            → 401
 *   2. Invalid signature   → 401
 *   3. Expired token       → 401
 *   4. Wrong issuer        → 401
 *   5. Not-before in future → 401
 *   6. Valid token         → proxied (200)
 *   7. X-User-Id header    → propagated from JWT sub claim
 *   8. /actuator/**        → public (no token required)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class JwtAuthenticationTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String TEST_ISSUER = "http://test-issuer/realms/sentinel";

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-key-1").generate();

        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        registerStubs();

        String base = "http://localhost:" + wireMock.port();

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> TEST_ISSUER);

        // Single route for auth testing
        registry.add("sentinel.gateway.routes[0].route-id",    () -> "user-service");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/users/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
    }

    private static void registerStubs() throws Exception {
        JWKSet publicJwkSet = new JWKSet(rsaKey.toPublicJWK());
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(publicJwkSet.toString())));

        wireMock.stubFor(get(urlPathMatching("/api/users.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[]}")));
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildJwt(String issuer, Instant expiry) throws Exception {
        return buildJwt(rsaKey, issuer, expiry);
    }

    private String buildJwt(RSAKey signingKey, String issuer, Instant expiry) throws Exception {
        var signer = new RSASSASigner(signingKey);
        var realmAccess = Map.of("roles", List.of("USER"));
        var claims = new JWTClaimsSet.Builder()
                .subject("user-test-123")
                .issuer(issuer)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(expiry))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", "openid profile")
                .claim("tenant_id", "tenant-acme")
                .claim("realm_access", realmAccess)
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    // ── Tests: unauthenticated/invalid → 401 ─────────────────────────────────

    @Test
    void noToken_returns401() {
        webTestClient.get().uri("/api/users")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void expiredToken_returns401() throws Exception {
        String token = buildJwt(TEST_ISSUER, Instant.now().minusSeconds(10));
        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void wrongIssuer_returns401() throws Exception {
        String token = buildJwt("http://malicious-issuer/realm", Instant.now().plusSeconds(300));
        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void invalidSignature_returns401() throws Exception {
        // Signed with a different key — not in the JWKS
        RSAKey wrongKey = new RSAKeyGenerator(2048).keyID("test-key-1").generate();
        String token = buildJwt(wrongKey, TEST_ISSUER, Instant.now().plusSeconds(300));
        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void notBeforeInFuture_returns401() throws Exception {
        var signer = new RSASSASigner(rsaKey);
        var claims = new JWTClaimsSet.Builder()
                .subject("user-test-123")
                .issuer(TEST_ISSUER)
                .issueTime(Date.from(Instant.now()))
                .notBeforeTime(Date.from(Instant.now().plusSeconds(600))) // nbf 10 min in future
                .expirationTime(Date.from(Instant.now().plusSeconds(900)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        jwt.sign(signer);
        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + jwt.serialize())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── Tests: valid token → proxied ──────────────────────────────────────────

    @Test
    void validToken_proxiedToUpstreamAndReturns200() throws Exception {
        String token = buildJwt(TEST_ISSUER, Instant.now().plusSeconds(300));
        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void validToken_xUserIdHeaderPropagatedToUpstream() throws Exception {
        String token = buildJwt(TEST_ISSUER, Instant.now().plusSeconds(300));
        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).consumeWith(b -> {});
        // JwtHeadersFilter + JwtPrincipalExtractor must have forwarded verified identity headers
        wireMock.verify(getRequestedFor(urlPathMatching("/api/users.*"))
                .withHeader("X-User-Id", equalTo("user-test-123"))
                .withHeader("X-Tenant-Id", equalTo("tenant-acme"))
                .withHeader("X-User-Roles", equalTo("USER")));
    }

    // ── Tests: public endpoints ───────────────────────────────────────────────

    @Test
    void actuatorHealth_noTokenRequired_returns200() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
