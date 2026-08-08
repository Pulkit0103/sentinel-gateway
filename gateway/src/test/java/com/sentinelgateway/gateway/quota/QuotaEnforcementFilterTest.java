package com.sentinelgateway.gateway.quota;

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
 * Tests for quota enforcement filter HTTP behaviour.
 * QuotaChecker is mocked — this tests the filter's HTTP response shaping
 * without requiring a running Redis instance.
 *
 * Scenarios:
 *   Quota checker allows  → 200 + X-Quota-* headers
 *   Quota exceeded        → 429 + X-Quota-* headers
 *   No tenant in JWT      → 200 (quota not checked for tenantless callers)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class QuotaEnforcementFilterTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String TEST_ISSUER = "http://test-quota-issuer/realms/sentinel";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private QuotaChecker quotaChecker;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("quota-test-key").generate();
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
        registry.add("sentinel.quota.enabled", () -> "true");

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

    private String token(String tenantId, String... roles) throws Exception {
        var signer = new RSASSASigner(rsaKey);
        var builder = new JWTClaimsSet.Builder()
                .subject("user-" + UUID.randomUUID())
                .issuer(TEST_ISSUER)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("realm_access", Map.of("roles", List.of(roles)));

        if (tenantId != null) builder.claim("tenant_id", tenantId);

        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                builder.build());
        jwt.sign(signer);
        return jwt.serialize();
    }

    // ── quota allowed ─────────────────────────────────────────────────────────

    @Test
    void withinQuota_returns200_withQuotaHeaders() throws Exception {
        when(quotaChecker.checkAndIncrement(anyString(), any()))
                .thenReturn(Mono.just(QuotaResult.allowed(100_000, 42, QuotaPeriod.DAILY, 1735689600L)));

        webTestClient.get().uri("/api/users/1")
                .header("Authorization", "Bearer " + token("tenant-acme", "USER"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Quota-Limit")
                .expectHeader().exists("X-Quota-Used")
                .expectHeader().exists("X-Quota-Reset");
    }

    // ── quota exceeded ────────────────────────────────────────────────────────

    @Test
    void quotaExceeded_returns429_withQuotaHeaders() throws Exception {
        when(quotaChecker.checkAndIncrement(anyString(), any()))
                .thenReturn(Mono.just(QuotaResult.exceeded(100_000, 100_001, QuotaPeriod.DAILY, 1735689600L)));

        webTestClient.get().uri("/api/users/1")
                .header("Authorization", "Bearer " + token("tenant-acme", "USER"))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("X-Quota-Limit", "100000")
                .expectHeader().exists("X-Quota-Used")
                .expectHeader().exists("X-Quota-Reset");
    }

    // ── no tenant ─────────────────────────────────────────────────────────────

    @Test
    void noTenantInJwt_quotaNotChecked_returns200() throws Exception {
        // QuotaChecker is NOT called for tenantless callers
        webTestClient.get().uri("/api/users/1")
                .header("Authorization", "Bearer " + token(null, "USER"))
                .exchange()
                .expectStatus().isOk();
    }
}
