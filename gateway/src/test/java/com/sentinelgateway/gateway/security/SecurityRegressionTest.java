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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive security regression suite — covers the full attack surface in one test class.
 *
 * Complements the unit-focused tests in JwtAuthenticationTest, ApiKeyAuthenticationTest, etc.
 * Tests here focus on:
 *   - JWT boundary conditions not covered elsewhere (malformed, no Bearer prefix, role-less)
 *   - Authorization boundary (admin endpoints, actuator publicity)
 *   - API key lifecycle security (create → revoke → verify rejection)
 *   - WAF injection blocking (SQLi, XSS, command injection)
 *   - Tenant isolation under header-spoofing attempts
 *   - CORS preflight passes without authentication
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SecurityRegressionTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String TEST_ISSUER = "http://security-suite-issuer/realms/sentinel";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ApiKeyService apiKeyService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("security-suite-key").generate();

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
        registry.add("sentinel.gateway.routes[0].route-id",    () -> "user-service");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/users/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");

        registry.add("sentinel.threat-detection.enabled",         () -> "true");
        registry.add("sentinel.threat-detection.block-threshold", () -> "40");
        registry.add("sentinel.threat-detection.log-threshold",   () -> "10");
        registry.add("sentinel.threat-detection.alert-threshold", () -> "70");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String validJwt() throws Exception {
        return validJwtWithRoles(List.of("USER"));
    }

    private String validJwtWithRoles(List<String> roles) throws Exception {
        var signer = new RSASSASigner(rsaKey);
        var claims = new JWTClaimsSet.Builder()
                .subject("sec-suite-user")
                .issuer(TEST_ISSUER)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("tenant_id", "tenant-acme")
                .claim("realm_access", Map.of("roles", roles))
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private WebTestClient userClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Group 1: JWT Boundary Conditions
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void malformedToken_notAJwt_returns401() {
        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer this.is.not.a.valid.jwt.token.at.all")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void garbageToken_randomString_returns401() {
        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + UUID.randomUUID())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void bearerPrefixMissing_tokenDirectly_returns401() throws Exception {
        // Authorization header must start with "Bearer " — bare JWT is rejected
        webTestClient.get().uri("/api/users")
                .header("Authorization", validJwt())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void tokenWithNoRoles_returns200_routeHasNoScopeRequirement() throws Exception {
        // A valid JWT with no roles still authenticates (route has no scope requirement)
        // The token is valid; route access should be allowed (no requiredScopes on this route)
        String token = validJwtWithRoles(List.of());
        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void tokenWithAdminRole_canAccessAdminEndpoint() {
        // ADMIN role is required for /admin/**; mock a JWT with ROLE_ADMIN
        adminClient().get().uri("/admin/routes")
                .exchange()
                .expectStatus().isOk();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Group 2: Authorization Boundaries
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void adminEndpoint_noToken_returns401() {
        webTestClient.get().uri("/admin/routes")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void adminEndpoint_userRole_returns403() {
        // Authenticated but without ADMIN role — must be 403 Forbidden (not 401)
        userClient().get().uri("/admin/routes")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void actuatorHealth_noAuth_returns200() {
        // /actuator/** is always public — no token required
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void actuatorPrometheus_noAuth_returns200() {
        // Prometheus scrape endpoint must be publicly accessible (no auth)
        webTestClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void corsPreflightOptions_noAuth_notUnauthorized() {
        // OPTIONS preflight must not require authentication — CORS would break otherwise.
        // The response may be 200, 204, or 403 (if CORS origin not in allowlist) but never 401.
        webTestClient.options().uri("/api/users")
                .header("Origin", "http://localhost:3001")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().value(status ->
                        assertNotEquals(401, status,
                                "OPTIONS preflight must not return 401 — authentication not required for preflight"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Group 3: API Key Lifecycle Security
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void apiKey_createThenRevoke_revokedKeyReturns401() {
        // Create a key, verify it works, revoke it, verify it no longer works
        var created = apiKeyService
                .create("revoke-test-client", "tenant-acme", "read", Instant.now().plusSeconds(3600))
                .block();
        assert created != null;
        String rawKey = created.rawKey();
        Long keyId = created.entity().getId();

        // Active key must authenticate
        webTestClient.get().uri("/api/users")
                .header("X-API-Key", rawKey)
                .exchange()
                .expectStatus().isOk();

        // Revoke and verify rejection
        apiKeyService.revoke(keyId).block();

        webTestClient.get().uri("/api/users")
                .header("X-API-Key", rawKey)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void apiKey_preExpired_returns401() {
        var created = apiKeyService
                .create("expired-test-client", "tenant-acme", "read", Instant.now().minusSeconds(1))
                .block();
        assert created != null;

        webTestClient.get().uri("/api/users")
                .header("X-API-Key", created.rawKey())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void apiKey_nonExistent_returns401() {
        webTestClient.get().uri("/api/users")
                .header("X-API-Key", "sgk_completely_made_up_key_that_does_not_exist")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void apiKey_emptyValue_returns401() {
        webTestClient.get().uri("/api/users")
                .header("X-API-Key", "")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Group 4: WAF Injection Blocking
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void sqlInjection_inQueryParam_returns400() throws Exception {
        // SQL injection pattern (UNION SELECT) → ThreatDetectionFilter blocks at score=60 (>40 threshold)
        String token = validJwt();
        webTestClient.get()
                .uri(u -> u.path("/api/users").queryParam("id", "1' UNION SELECT * FROM users; --").build())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void xssPattern_inQueryParam_returns400() throws Exception {
        // XSS pattern → ThreatDetectionFilter blocks at score=50 (>40 threshold)
        String token = validJwt();
        webTestClient.get()
                .uri(u -> u.path("/api/users").queryParam("q", "<script>alert(1)</script>").build())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void commandInjection_inQueryParam_returns400() throws Exception {
        // Command injection → ThreatDetectionFilter blocks at score=80 (>40 threshold)
        String token = validJwt();
        webTestClient.get()
                .uri(u -> u.path("/api/users").queryParam("cmd", "; ls -la").build())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void pathTraversal_inPath_loggedButNotBlocked() throws Exception {
        // Path traversal score=30 is below the block threshold (50) — logged but allowed through
        // (the request may still 404/200 depending on upstream; it is NOT a 400 WAF block)
        String token = validJwt();
        webTestClient.get().uri("/api/users/../config.properties")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status ->
                        assertNotEquals(400, status,
                                "Path traversal at score=30 should not be WAF-blocked (400)"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Group 5: Tenant Isolation
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void tenantSpoofingViaHeader_jwtTenantWins() throws Exception {
        // Sending X-Tenant-Id header must NOT override the JWT-derived tenant.
        // The upstream should receive the JWT's tenant_id, not the header value.
        String token = validJwt(); // tenant_id = "tenant-acme" in JWT

        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-Id", "tenant-evil-corp")  // spoofed — must be ignored/overwritten
                .exchange()
                .expectStatus().isOk();

        // Upstream must receive "tenant-acme" from the JWT, not "tenant-evil-corp"
        wireMock.verify(getRequestedFor(urlPathMatching("/api/users.*"))
                .withHeader("X-Tenant-Id", equalTo("tenant-acme")));
    }
}
