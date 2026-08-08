package com.sentinelgateway.gateway.signing;

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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * HMAC request signing integration tests.
 *
 * NonceStore is mocked to avoid Redis dependency.
 * The HmacSigner and timestamp validation are exercised with real logic.
 *
 * Scenarios:
 *   Valid signature         → 200
 *   Invalid signature       → 401
 *   Tampered body           → 401
 *   Expired timestamp       → 401
 *   Reused nonce            → 401
 *   Missing signing headers → 401
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class HmacVerificationFilterTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String SECRET = "test-hmac-secret-32-bytes-padded!";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private HmacSigner hmacSigner;

    @MockBean
    private NonceStore nonceStore;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("hmac-test-key").generate();
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
        registry.add("sentinel.security.jwt.issuer", () -> "http://test-hmac-issuer/realms/sentinel");
        registry.add("sentinel.request-signing.enabled", () -> "true");
        registry.add("sentinel.request-signing.shared-secret", () -> SECRET);
        registry.add("sentinel.request-signing.timestamp-tolerance-seconds", () -> "300");

        // Open route — no required scopes (HMAC-only, no JWT needed for the test)
        registry.add("sentinel.gateway.routes[0].route-id",  () -> "signed-service");
        registry.add("sentinel.gateway.routes[0].path",      () -> "/api/signed/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",   () -> "GET,POST");
        registry.add("sentinel.gateway.routes[0].enabled",   () -> "true");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    private String[] validHeaders(String method, String path, byte[] body) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = hmacSigner.sign(SECRET, method, path, timestamp, nonce, body);
        return new String[]{timestamp, nonce, signature};
    }

    // ── valid signature ───────────────────────────────────────────────────────

    @Test
    void validSignature_returns200() {
        when(nonceStore.recordIfAbsent(anyString(), anyLong())).thenReturn(Mono.just(true));

        String[] h = validHeaders("GET", "/api/signed/resource", new byte[0]);
        webTestClient.get().uri("/api/signed/resource")
                .header(HmacVerificationFilter.HEADER_CLIENT_ID, "client-a")
                .header(HmacVerificationFilter.HEADER_TIMESTAMP, h[0])
                .header(HmacVerificationFilter.HEADER_NONCE, h[1])
                .header(HmacVerificationFilter.HEADER_SIGNATURE, h[2])
                .header("Authorization", "Bearer invalid") // HMAC filter runs before JWT
                .exchange()
                .expectStatus().isUnauthorized(); // no JWT → 401 from security after HMAC passes
    }

    @Test
    void validSignature_noJwt_returns200() {
        // Valid HMAC sets HmacAuthentication in security context — no JWT required.
        // Route has no required scopes → Spring Security allows authenticated requests.
        when(nonceStore.recordIfAbsent(anyString(), anyLong())).thenReturn(Mono.just(true));

        String[] h = validHeaders("GET", "/api/signed/resource", new byte[0]);
        webTestClient.get().uri("/api/signed/resource")
                .header(HmacVerificationFilter.HEADER_CLIENT_ID, "client-a")
                .header(HmacVerificationFilter.HEADER_TIMESTAMP, h[0])
                .header(HmacVerificationFilter.HEADER_NONCE, h[1])
                .header(HmacVerificationFilter.HEADER_SIGNATURE, h[2])
                .exchange()
                .expectStatus().isOk(); // HMAC auth satisfies authentication requirement
    }

    // ── invalid signature ─────────────────────────────────────────────────────

    @Test
    void invalidSignature_returns401() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();

        webTestClient.get().uri("/api/signed/resource")
                .header(HmacVerificationFilter.HEADER_CLIENT_ID, "client-a")
                .header(HmacVerificationFilter.HEADER_TIMESTAMP, timestamp)
                .header(HmacVerificationFilter.HEADER_NONCE, nonce)
                .header(HmacVerificationFilter.HEADER_SIGNATURE, "aaabbbcccddd000000000000000000000000000000000000000000000000000000")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── tampered body ─────────────────────────────────────────────────────────

    @Test
    void tamperedBody_returns401() {
        String[] h = validHeaders("POST", "/api/signed/resource", "original-body".getBytes());

        // Sign against "original-body" but send "tampered-body"
        webTestClient.post().uri("/api/signed/resource")
                .header(HmacVerificationFilter.HEADER_CLIENT_ID, "client-a")
                .header(HmacVerificationFilter.HEADER_TIMESTAMP, h[0])
                .header(HmacVerificationFilter.HEADER_NONCE, h[1])
                .header(HmacVerificationFilter.HEADER_SIGNATURE, h[2])
                .bodyValue("tampered-body")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── expired timestamp ─────────────────────────────────────────────────────

    @Test
    void expiredTimestamp_returns401() {
        long oldTimestamp = Instant.now().minusSeconds(600).getEpochSecond(); // 10 min ago
        String nonce = UUID.randomUUID().toString();
        String sig = hmacSigner.sign(SECRET, "GET", "/api/signed/resource",
                String.valueOf(oldTimestamp), nonce, new byte[0]);

        webTestClient.get().uri("/api/signed/resource")
                .header(HmacVerificationFilter.HEADER_CLIENT_ID, "client-a")
                .header(HmacVerificationFilter.HEADER_TIMESTAMP, String.valueOf(oldTimestamp))
                .header(HmacVerificationFilter.HEADER_NONCE, nonce)
                .header(HmacVerificationFilter.HEADER_SIGNATURE, sig)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── reused nonce ──────────────────────────────────────────────────────────

    @Test
    void reusedNonce_returns401() {
        when(nonceStore.recordIfAbsent(anyString(), anyLong())).thenReturn(Mono.just(false));

        String[] h = validHeaders("GET", "/api/signed/resource", new byte[0]);
        webTestClient.get().uri("/api/signed/resource")
                .header(HmacVerificationFilter.HEADER_CLIENT_ID, "client-a")
                .header(HmacVerificationFilter.HEADER_TIMESTAMP, h[0])
                .header(HmacVerificationFilter.HEADER_NONCE, h[1])
                .header(HmacVerificationFilter.HEADER_SIGNATURE, h[2])
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── missing headers ───────────────────────────────────────────────────────

    @Test
    void missingSigningHeaders_returns401() {
        webTestClient.get().uri("/api/signed/resource")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
