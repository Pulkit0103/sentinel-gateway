package com.sentinelgateway.gateway.revocation;

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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link JwtRevocationFilter}.
 *
 * Uses WireMock for upstream and JWKS, and mocks {@link TokenRevocationService}
 * to avoid needing a real Redis instance.
 *
 * Tests:
 *   1. Valid token, not revoked → 200 proxied to upstream
 *   2. Valid token, revoked     → 401 with JSON error body
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RevocationFilterTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String TEST_ISSUER = "http://test-revocation-issuer/realms/sentinel";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TokenRevocationService mockRevocationService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("revocation-test-key").generate();

        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        registerStubs();

        String base = "http://localhost:" + wireMock.port();

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> TEST_ISSUER);

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

    private String buildJwt(String jti) throws Exception {
        var signer = new RSASSASigner(rsaKey);
        var claims = new JWTClaimsSet.Builder()
                .subject("user-test-" + UUID.randomUUID())
                .issuer(TEST_ISSUER)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(jti)
                .claim("scope", "openid profile")
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    @Test
    void validToken_notRevoked_passes200() throws Exception {
        String jti = UUID.randomUUID().toString();
        when(mockRevocationService.isRevoked(anyString())).thenReturn(Mono.just(false));

        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + buildJwt(jti))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void revokedToken_returns401() throws Exception {
        String jti = UUID.randomUUID().toString();
        when(mockRevocationService.isRevoked(anyString())).thenReturn(Mono.just(true));

        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + buildJwt(jti))
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
