package com.sentinelgateway.gateway.transform;

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
 * Integration tests for RequestSanitizationFilter.
 *
 * Verifies that client-supplied identity headers are stripped before reaching upstream,
 * while legitimate headers (e.g. X-Request-Id) are forwarded unchanged.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RequestSanitizationFilterTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String TEST_ISSUER = "http://test-sanitize-issuer/realms/sentinel";

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("sanitize-test-key").generate();

        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        JWKSet jwks = new JWKSet(rsaKey.toPublicJWK());
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwks.toString())));

        wireMock.stubFor(any(urlPathMatching("/api/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        String base = "http://localhost:" + wireMock.port();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> TEST_ISSUER);

        registry.add("sentinel.gateway.routes[0].route-id",    () -> "sanitize-test-service");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/sanitize/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    private String jwt() throws Exception {
        var signer = new RSASSASigner(rsaKey);
        var claims = new JWTClaimsSet.Builder()
                .subject("real-user-123")
                .issuer(TEST_ISSUER)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("tenant_id", "tenant-real")
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    @Test
    void clientSentXUserId_isStrippedBeforeUpstream() throws Exception {
        webTestClient.get().uri("/api/sanitize/resource")
                .header("Authorization", "Bearer " + jwt())
                .header("X-User-Id", "attacker-injected")
                .exchange()
                .expectStatus().isOk();

        // RequestSanitizationFilter strips the client-injected value;
        // JwtHeadersFilter then adds the real value from the validated JWT.
        // Upstream must see the real identity, never the attacker-supplied value.
        wireMock.verify(getRequestedFor(urlPathMatching("/api/sanitize/.*"))
                .withHeader("X-User-Id", equalTo("real-user-123")));
    }

    @Test
    void xInternalHeader_isStrippedBeforeUpstream() throws Exception {
        webTestClient.get().uri("/api/sanitize/resource")
                .header("Authorization", "Bearer " + jwt())
                .header("X-Internal-Secret", "should-not-reach-upstream")
                .exchange()
                .expectStatus().isOk();

        wireMock.verify(getRequestedFor(urlPathMatching("/api/sanitize/.*"))
                .withHeader("X-Internal-Secret", absent()));
    }

    @Test
    void legitimateHeader_isForwarded() throws Exception {
        String requestId = "legitimate-request-id-" + UUID.randomUUID();
        webTestClient.get().uri("/api/sanitize/resource")
                .header("Authorization", "Bearer " + jwt())
                .header("X-Request-Id", requestId)
                .exchange()
                .expectStatus().isOk();

        wireMock.verify(getRequestedFor(urlPathMatching("/api/sanitize/.*"))
                .withHeader("X-Request-Id", equalTo(requestId)));
    }
}
