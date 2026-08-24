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
 * Integration tests for Phase 15: Configurable JWT Claims Forwarding.
 *
 * Verifies that when {@code sentinel.jwt.claims-forwarding.enabled=true} and
 * a claim-to-header mapping is configured, the specified JWT claim value is
 * forwarded as an HTTP header to the upstream service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class JwtClaimsForwardingTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String TEST_ISSUER = "http://test-issuer/realms/sentinel";

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("claims-fwd-key-1").generate();

        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        registerStubs();

        String base = "http://localhost:" + wireMock.port();

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> TEST_ISSUER);

        // Enable claims forwarding and add email → X-User-Email mapping
        registry.add("sentinel.jwt.claims-forwarding.enabled", () -> "true");
        registry.add("sentinel.jwt.claims-forwarding.mappings[0].claim", () -> "email");
        registry.add("sentinel.jwt.claims-forwarding.mappings[0].header", () -> "X-User-Email");

        // Single route backed by WireMock
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

    private String buildJwtWithEmail(String email) throws Exception {
        var signer = new RSASSASigner(rsaKey);
        var realmAccess = Map.of("roles", List.of("USER"));
        var claims = new JWTClaimsSet.Builder()
                .subject("user-claims-fwd-001")
                .issuer(TEST_ISSUER)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", "openid profile")
                .claim("tenant_id", "tenant-test")
                .claim("realm_access", realmAccess)
                .claim("email", email)
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    @Test
    void emailClaim_forwardedAsXUserEmailHeader() throws Exception {
        String token = buildJwtWithEmail("test@example.com");

        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        wireMock.verify(getRequestedFor(urlPathMatching("/api/users.*"))
                .withHeader("X-User-Email", equalTo("test@example.com")));
    }

    @Test
    void standardHeadersStillPresent_whenClaimsForwardingEnabled() throws Exception {
        String token = buildJwtWithEmail("another@example.com");

        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        // Standard identity headers must still be forwarded alongside the extra claim header
        wireMock.verify(getRequestedFor(urlPathMatching("/api/users.*"))
                .withHeader("X-User-Id", equalTo("user-claims-fwd-001"))
                .withHeader("X-User-Email", equalTo("another@example.com")));
    }
}
