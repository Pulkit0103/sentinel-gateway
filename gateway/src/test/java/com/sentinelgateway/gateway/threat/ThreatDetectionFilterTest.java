package com.sentinelgateway.gateway.threat;

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
 * Integration tests for Phase 12: Threat Detection.
 *
 * A single route is configured; WireMock returns 200 for all matching requests.
 * Threat detection is enabled with low thresholds to exercise all action levels:
 *   logThreshold=10, blockThreshold=40, alertThreshold=70
 *
 * Scenarios:
 *   Clean request                     → 200 (ALLOW)
 *   Path traversal (score=30)         → 200 (LOG — below block threshold)
 *   SQL injection (score=60)          → 400 (BLOCK)
 *   XSS pattern (score=50)            → 400 (BLOCK)
 *   Command injection (score=80)      → 400 (BLOCK_AND_ALERT)
 *   Blocked IP (X-Forwarded-For)      → 403 (IP block)
 *   Stacked patterns (SQLi+XSS=110)   → 400 (BLOCK_AND_ALERT)
 *   Actuator endpoint                 → 200 (exempt from WAF)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ThreatDetectionFilterTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String ISSUER = "http://test-threat-issuer/realms/sentinel";
    private static final String BLOCKED_IP = "10.0.0.99";

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("threat-test-key").generate();
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
        registry.add("sentinel.security.jwt.issuer", () -> ISSUER);

        // Threat detection — enabled with low thresholds for precise test control
        registry.add("sentinel.threat-detection.enabled",           () -> "true");
        registry.add("sentinel.threat-detection.log-threshold",     () -> "10");
        registry.add("sentinel.threat-detection.block-threshold",   () -> "40");
        registry.add("sentinel.threat-detection.alert-threshold",   () -> "70");
        registry.add("sentinel.threat-detection.blocked-ips[0]",   () -> BLOCKED_IP);

        // Route for WAF testing
        registry.add("sentinel.gateway.routes[0].route-id",    () -> "threat-test-service");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/threat/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET,POST");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String jwt() throws Exception {
        var signer = new RSASSASigner(rsaKey);
        var claims = new JWTClaimsSet.Builder()
                .subject("threat-test-user")
                .issuer(ISSUER)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", "openid profile")
                .claim("tenant_id", "tenant-test")
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    // ── ALLOW ────────────────────────────────────────────────────────────────

    @Test
    void cleanRequest_returns200() throws Exception {
        webTestClient.get().uri("/api/threat/users")
                .header("Authorization", "Bearer " + jwt())
                .exchange()
                .expectStatus().isOk();
    }

    // ── LOG (path traversal, score=30, below block threshold of 40) ──────────

    @Test
    void pathTraversal_belowBlockThreshold_logsAndPasses() throws Exception {
        // Score = 30 < block-threshold=40 → LOG action, request passes through
        webTestClient.get().uri("/api/threat/files?path=../config.properties")
                .header("Authorization", "Bearer " + jwt())
                .exchange()
                .expectStatus().isOk();
    }

    // ── BLOCK ─────────────────────────────────────────────────────────────────

    @Test
    void sqlInjection_returns400() throws Exception {
        // Score = 60 >= block-threshold=40 → BLOCK
        webTestClient.get().uri("/api/threat/users?name=' UNION SELECT * FROM users; --")
                .header("Authorization", "Bearer " + jwt())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void xssPattern_returns400() throws Exception {
        // Score = 50 >= block-threshold=40 → BLOCK
        webTestClient.get().uri("/api/threat/search?q=<script>alert(1)</script>")
                .header("Authorization", "Bearer " + jwt())
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── BLOCK_AND_ALERT ───────────────────────────────────────────────────────

    @Test
    void commandInjection_returns400() throws Exception {
        // Score = 80 >= alert-threshold=70 → BLOCK_AND_ALERT (still 400)
        webTestClient.get().uri("/api/threat/exec?cmd=;+ls+-la")
                .header("Authorization", "Bearer " + jwt())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void stackedPatterns_sqlPlusXss_returns400() throws Exception {
        // SQLi (60) + XSS (50) = 110 >= alert-threshold=70 → BLOCK_AND_ALERT
        webTestClient.get().uri("/api/threat/search?q=<script>%20UNION%20SELECT%20*%20FROM%20users")
                .header("Authorization", "Bearer " + jwt())
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── Blocked IP ────────────────────────────────────────────────────────────

    @Test
    void blockedIp_returns403() throws Exception {
        webTestClient.get().uri("/api/threat/users")
                .header("Authorization", "Bearer " + jwt())
                .header("X-Forwarded-For", BLOCKED_IP)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Actuator exempt ───────────────────────────────────────────────────────

    @Test
    void actuatorHealth_exemptFromWaf_returns200() {
        // Actuator is public (no JWT needed) and exempt from WAF scanning
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
