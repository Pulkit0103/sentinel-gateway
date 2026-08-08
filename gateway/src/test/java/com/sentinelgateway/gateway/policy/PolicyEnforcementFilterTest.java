package com.sentinelgateway.gateway.policy;

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
import com.sentinelgateway.gateway.signing.HmacSigner;
import com.sentinelgateway.gateway.signing.HmacVerificationFilter;
import com.sentinelgateway.gateway.signing.NonceStore;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Phase 11: Security Policy Engine.
 *
 * Three routes are configured:
 *   /api/policy-mfa/**     — policy requires MFA (amr=mfa in JWT)
 *   /api/policy-signing/** — policy requires HMAC request signing
 *   /api/policy-open/**    — no policy (pass-through)
 *
 * NonceStore is mocked to avoid Redis; HmacSigner is exercised with real logic.
 *
 * Scenarios:
 *   No policy for route                           → 200
 *   MFA required, JWT without amr=mfa             → 403
 *   MFA required, JWT with amr=[mfa]              → 200
 *   Signing required, JWT-only auth               → 401
 *   Signing required, valid HMAC auth             → 200
 *   MFA required, no JWT (missing auth)           → 401 (Spring Security)
 *   Signing required, no auth at all              → 401 (Spring Security)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PolicyEnforcementFilterTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String ISSUER = "http://test-policy-issuer/realms/sentinel";
    private static final String HMAC_SECRET = "policy-engine-test-hmac-secret-32b!";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private HmacSigner hmacSigner;

    @MockBean
    private NonceStore nonceStore;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("policy-test-key").generate();
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        JWKSet jwks = new JWKSet(rsaKey.toPublicJWK());
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwks.toString())));
        wireMock.stubFor(get(urlPathMatching("/api/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        String base = "http://localhost:" + wireMock.port();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> ISSUER);

        // Enable HMAC signing so HmacVerificationFilter is active for signing tests
        registry.add("sentinel.request-signing.enabled",            () -> "true");
        registry.add("sentinel.request-signing.shared-secret",      () -> HMAC_SECRET);
        registry.add("sentinel.request-signing.timestamp-tolerance-seconds", () -> "300");

        // Routes
        registry.add("sentinel.gateway.routes[0].route-id",    () -> "policy-mfa-service");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/policy-mfa/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");

        registry.add("sentinel.gateway.routes[1].route-id",    () -> "policy-signing-service");
        registry.add("sentinel.gateway.routes[1].path",        () -> "/api/policy-signing/**");
        registry.add("sentinel.gateway.routes[1].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[1].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[1].enabled",     () -> "true");

        registry.add("sentinel.gateway.routes[2].route-id",    () -> "policy-open-service");
        registry.add("sentinel.gateway.routes[2].path",        () -> "/api/policy-open/**");
        registry.add("sentinel.gateway.routes[2].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[2].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[2].enabled",     () -> "true");

        // Policies — loaded into DB by SecurityPolicyLoader at startup
        registry.add("sentinel.policy.policies[0].policy-id",              () -> "mfa-policy");
        registry.add("sentinel.policy.policies[0].route-id",               () -> "policy-mfa-service");
        registry.add("sentinel.policy.policies[0].require-mfa",            () -> "true");
        registry.add("sentinel.policy.policies[0].request-signing-required", () -> "false");

        registry.add("sentinel.policy.policies[1].policy-id",              () -> "signing-policy");
        registry.add("sentinel.policy.policies[1].route-id",               () -> "policy-signing-service");
        registry.add("sentinel.policy.policies[1].require-mfa",            () -> "false");
        registry.add("sentinel.policy.policies[1].request-signing-required", () -> "true");
        // policy-open-service intentionally has no policy entry
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String jwtWithoutMfa() throws Exception {
        return buildJwt(/* amr */ null);
    }

    private String jwtWithMfa() throws Exception {
        return buildJwt(List.of("mfa"));
    }

    private String buildJwt(List<String> amr) throws Exception {
        var signer = new RSASSASigner(rsaKey);
        var builder = new JWTClaimsSet.Builder()
                .subject("policy-test-user")
                .issuer(ISSUER)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", "openid profile")
                .claim("tenant_id", "tenant-test")
                .claim("realm_access", Map.of("roles", List.of("USER")));
        if (amr != null) {
            builder.claim("amr", amr);
        }
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                builder.build());
        jwt.sign(signer);
        return jwt.serialize();
    }

    private String[] hmacHeaders(String method, String path) {
        String ts    = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String sig   = hmacSigner.sign(HMAC_SECRET, method, path, ts, nonce, new byte[0]);
        return new String[]{ts, nonce, sig};
    }

    // ── no policy → pass-through ──────────────────────────────────────────────

    @Test
    void noPolicyForRoute_validJwt_returns200() throws Exception {
        webTestClient.get().uri("/api/policy-open/data")
                .header("Authorization", "Bearer " + jwtWithoutMfa())
                .exchange()
                .expectStatus().isOk();
    }

    // ── MFA enforcement ───────────────────────────────────────────────────────

    @Test
    void mfaRequired_jwtWithoutMfaClaim_returns403() throws Exception {
        webTestClient.get().uri("/api/policy-mfa/data")
                .header("Authorization", "Bearer " + jwtWithoutMfa())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void mfaRequired_jwtWithMfaClaim_returns200() throws Exception {
        webTestClient.get().uri("/api/policy-mfa/data")
                .header("Authorization", "Bearer " + jwtWithMfa())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void mfaRequired_noAuth_returns401() {
        webTestClient.get().uri("/api/policy-mfa/data")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── signing enforcement ───────────────────────────────────────────────────

    @Test
    void signingRequired_jwtOnlyAuth_returns401() throws Exception {
        // No HMAC headers — HmacVerificationFilter passes through, JWT authenticates
        // the caller, but SigningRequiredPolicyCheck rejects because auth is JWT not HMAC.
        webTestClient.get().uri("/api/policy-signing/resource")
                .header("Authorization", "Bearer " + jwtWithoutMfa())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void signingRequired_validHmacAuth_returns200() {
        when(nonceStore.recordIfAbsent(anyString(), anyLong())).thenReturn(Mono.just(true));

        String[] h = hmacHeaders("GET", "/api/policy-signing/resource");
        webTestClient.get().uri("/api/policy-signing/resource")
                .header(HmacVerificationFilter.HEADER_CLIENT_ID, "client-policy-test")
                .header(HmacVerificationFilter.HEADER_TIMESTAMP, h[0])
                .header(HmacVerificationFilter.HEADER_NONCE,     h[1])
                .header(HmacVerificationFilter.HEADER_SIGNATURE, h[2])
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void signingRequired_noAuth_returns401() {
        webTestClient.get().uri("/api/policy-signing/resource")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
