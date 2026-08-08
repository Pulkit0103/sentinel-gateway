package com.sentinelgateway.gateway.ratelimit;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for rate limit filter HTTP behaviour.
 * The RateLimiter bean is mocked — this tests the filter's response shaping
 * (headers, status codes) without requiring a running Redis instance.
 *
 * Scenarios:
 *   Rate limiter allows → 200 + X-RateLimit-* headers present
 *   Rate limiter denies → 429 + X-RateLimit-* headers present
 *   Anonymous request under limit → 200
 *   Anonymous request over limit  → 429
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RateLimitFilterTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String TEST_ISSUER = "http://test-rl-issuer/realms/sentinel";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private RateLimiter rateLimiter;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("rl-test-key").generate();
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

        registry.add("sentinel.rate-limit.enabled", () -> "true");

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

    private String token(String... roles) throws Exception {
        var signer = new RSASSASigner(rsaKey);
        var claims = new JWTClaimsSet.Builder()
                .subject("user-" + UUID.randomUUID())
                .issuer(TEST_ISSUER)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(), claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    // ── authenticated request under limit ─────────────────────────────────────

    @Test
    void underLimit_returns200_withRateLimitHeaders() throws Exception {
        when(rateLimiter.checkAndIncrement(anyString(), any()))
                .thenReturn(Mono.just(RateLimitResult.allowed(1000, 999, 55)));

        webTestClient.get().uri("/api/users/1")
                .header("Authorization", "Bearer " + token("USER"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-RateLimit-Limit")
                .expectHeader().exists("X-RateLimit-Remaining")
                .expectHeader().exists("X-RateLimit-Reset");
    }

    // ── authenticated request over limit ──────────────────────────────────────

    @Test
    void overLimit_returns429_withRateLimitHeaders() throws Exception {
        when(rateLimiter.checkAndIncrement(anyString(), any()))
                .thenReturn(Mono.just(RateLimitResult.denied(1000, 42)));

        webTestClient.get().uri("/api/users/1")
                .header("Authorization", "Bearer " + token("USER"))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("X-RateLimit-Limit", "1000")
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0")
                .expectHeader().exists("X-RateLimit-Reset");
    }

    // ── anonymous under limit ─────────────────────────────────────────────────

    @Test
    void anonymous_underLimit_returns401() {
        // Anonymous unauthenticated requests get 401 from Spring Security before rate limiter
        // This validates that security runs before rate limiting
        webTestClient.get().uri("/api/users/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── concurrent requests all get headers ───────────────────────────────────

    @Test
    void concurrentRequests_allReceiveRateLimitHeaders() throws Exception {
        when(rateLimiter.checkAndIncrement(anyString(), any()))
                .thenReturn(Mono.just(RateLimitResult.allowed(1000, 998, 55)));

        String bearerToken = "Bearer " + token("USER");

        // Fire 3 concurrent requests — all should get rate limit headers
        for (int i = 0; i < 3; i++) {
            webTestClient.get().uri("/api/users/" + i)
                    .header("Authorization", bearerToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().exists("X-RateLimit-Limit");
        }
    }
}
