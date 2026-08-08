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
 * Comprehensive RBAC authorization test matrix.
 *
 * Route setup:
 *   /api/users/**    requires USER_READ
 *   /api/orders/**   requires ORDER_READ
 *   /api/payments/** requires PAYMENT_READ
 *
 * Role-to-permission mapping under test (from RolePermissions):
 *   ADMIN   → all permissions
 *   USER    → USER_READ, USER_WRITE, ORDER_READ, ORDER_WRITE  (NO payment)
 *   SUPPORT → USER_READ, ORDER_READ, PAYMENT_READ
 *   SERVICE → all permissions
 *
 * Matrix (401 = unauthenticated, 403 = authenticated but unauthorized, 200 = OK):
 *   No token         → 401 (Spring Security)
 *   USER → users     → 200
 *   USER → orders    → 200
 *   USER → payments  → 403
 *   ADMIN → payments → 200
 *   SUPPORT → users  → 200
 *   SUPPORT → payment → 200
 *   SUPPORT + USER_WRITE required → 403 (SUPPORT cannot write)
 *   SERVICE → payments → 200
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RbacAuthorizationTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String TEST_ISSUER = "http://test-rbac-issuer/realms/sentinel";

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("rbac-test-key").generate();
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        registerStubs();

        String base = "http://localhost:" + wireMock.port();

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> TEST_ISSUER);

        // Route 0: users — requires USER_READ
        registry.add("sentinel.gateway.routes[0].route-id",              () -> "user-service");
        registry.add("sentinel.gateway.routes[0].path",                  () -> "/api/users/**");
        registry.add("sentinel.gateway.routes[0].service-uri",           () -> base);
        registry.add("sentinel.gateway.routes[0].methods",               () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",               () -> "true");
        registry.add("sentinel.gateway.routes[0].required-scopes[0]",   () -> "USER_READ");

        // Route 1: orders — requires ORDER_READ
        registry.add("sentinel.gateway.routes[1].route-id",              () -> "order-service");
        registry.add("sentinel.gateway.routes[1].path",                  () -> "/api/orders/**");
        registry.add("sentinel.gateway.routes[1].service-uri",           () -> base);
        registry.add("sentinel.gateway.routes[1].methods",               () -> "GET");
        registry.add("sentinel.gateway.routes[1].enabled",               () -> "true");
        registry.add("sentinel.gateway.routes[1].required-scopes[0]",   () -> "ORDER_READ");

        // Route 2: payments — requires PAYMENT_READ
        registry.add("sentinel.gateway.routes[2].route-id",              () -> "payment-service");
        registry.add("sentinel.gateway.routes[2].path",                  () -> "/api/payments/**");
        registry.add("sentinel.gateway.routes[2].service-uri",           () -> base);
        registry.add("sentinel.gateway.routes[2].methods",               () -> "GET");
        registry.add("sentinel.gateway.routes[2].enabled",               () -> "true");
        registry.add("sentinel.gateway.routes[2].required-scopes[0]",   () -> "PAYMENT_READ");

        // Route 3: write endpoint — requires USER_WRITE
        registry.add("sentinel.gateway.routes[3].route-id",              () -> "user-write");
        registry.add("sentinel.gateway.routes[3].path",                  () -> "/api/users-write/**");
        registry.add("sentinel.gateway.routes[3].service-uri",           () -> base);
        registry.add("sentinel.gateway.routes[3].methods",               () -> "POST");
        registry.add("sentinel.gateway.routes[3].enabled",               () -> "true");
        registry.add("sentinel.gateway.routes[3].required-scopes[0]",   () -> "USER_WRITE");
    }

    private static void registerStubs() throws Exception {
        JWKSet publicJwkSet = new JWKSet(rsaKey.toPublicJWK());
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(publicJwkSet.toString())));

        wireMock.stubFor(get(urlPathMatching("/api/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        wireMock.stubFor(post(urlPathMatching("/api/.*"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String token(String... roles) throws Exception {
        var signer = new RSASSASigner(rsaKey);
        var realmAccess = Map.of("roles", List.of(roles));
        var claims = new JWTClaimsSet.Builder()
                .subject("user-" + UUID.randomUUID())
                .issuer(TEST_ISSUER)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("realm_access", realmAccess)
                .claim("scope", "openid")
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    // ── 401: unauthenticated ──────────────────────────────────────────────────

    @Test
    void noToken_users_returns401() {
        webTestClient.get().uri("/api/users").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void noToken_payments_returns401() {
        webTestClient.get().uri("/api/payments").exchange().expectStatus().isUnauthorized();
    }

    // ── USER role ─────────────────────────────────────────────────────────────

    @Test
    void userRole_users_returns200() throws Exception {
        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + token("USER"))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).consumeWith(b -> {});
    }

    @Test
    void userRole_orders_returns200() throws Exception {
        webTestClient.get().uri("/api/orders")
                .header("Authorization", "Bearer " + token("USER"))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).consumeWith(b -> {});
    }

    @Test
    void userRole_payments_returns403() throws Exception {
        // USER role does not have PAYMENT_READ
        webTestClient.get().uri("/api/payments")
                .header("Authorization", "Bearer " + token("USER"))
                .exchange().expectStatus().isForbidden();
    }

    // ── ADMIN role ────────────────────────────────────────────────────────────

    @Test
    void adminRole_payments_returns200() throws Exception {
        webTestClient.get().uri("/api/payments")
                .header("Authorization", "Bearer " + token("ADMIN"))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).consumeWith(b -> {});
    }

    @Test
    void adminRole_users_returns200() throws Exception {
        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + token("ADMIN"))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).consumeWith(b -> {});
    }

    // ── SUPPORT role ──────────────────────────────────────────────────────────

    @Test
    void supportRole_users_returns200() throws Exception {
        webTestClient.get().uri("/api/users")
                .header("Authorization", "Bearer " + token("SUPPORT"))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).consumeWith(b -> {});
    }

    @Test
    void supportRole_payments_returns200() throws Exception {
        webTestClient.get().uri("/api/payments")
                .header("Authorization", "Bearer " + token("SUPPORT"))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).consumeWith(b -> {});
    }

    @Test
    void supportRole_writeEndpoint_returns403() throws Exception {
        // SUPPORT only has read permissions — USER_WRITE is for USER role
        webTestClient.post().uri("/api/users-write/resource")
                .header("Authorization", "Bearer " + token("SUPPORT"))
                .exchange().expectStatus().isForbidden();
    }

    // ── SERVICE role ──────────────────────────────────────────────────────────

    @Test
    void serviceRole_payments_returns200() throws Exception {
        webTestClient.get().uri("/api/payments")
                .header("Authorization", "Bearer " + token("SERVICE"))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).consumeWith(b -> {});
    }

    // ── Multi-role ────────────────────────────────────────────────────────────

    @Test
    void userAndSupportRole_payments_returns200() throws Exception {
        // USER alone can't access payments, but USER + SUPPORT can (SUPPORT grants PAYMENT_READ)
        webTestClient.get().uri("/api/payments")
                .header("Authorization", "Bearer " + token("USER", "SUPPORT"))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).consumeWith(b -> {});
    }

    @Test
    void unknownRole_payments_returns403() throws Exception {
        // An unrecognised role grants no permissions
        webTestClient.get().uri("/api/payments")
                .header("Authorization", "Bearer " + token("DEVELOPER"))
                .exchange().expectStatus().isForbidden();
    }
}
