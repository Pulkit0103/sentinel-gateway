package com.sentinelgateway.gateway.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ClaimAuthorizationFilter} (Phase 26: Claim-Based Authorization).
 *
 * Route "claim-auth-test" requires claim "department" to be one of ["engineering","finance"].
 *
 * Scenarios:
 *   1. JWT with matching claim value → 200
 *   2. JWT with wrong claim value → 403
 *   3. JWT missing the required claim → 403
 *   4. Filter disabled → any JWT passes
 *   5. 403 body has expected JSON fields
 *   6. Route with no requirement always passes
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ClaimAuthorizationFilterTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ClaimAuthorizationProperties properties;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));

        String base = "http://localhost:" + wireMock.port();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "");

        // Route with claim requirement
        registry.add("sentinel.gateway.routes[0].route-id",    () -> "claim-auth-test");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/claim-auth/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");

        // Enable claim authorization with requirement for the test route
        registry.add("sentinel.claim-authorization.enabled", () -> "true");
        registry.add("sentinel.claim-authorization.routes.claim-auth-test[0].claim",
                () -> "department");
        registry.add("sentinel.claim-authorization.routes.claim-auth-test[0].allowed-values[0]",
                () -> "engineering");
        registry.add("sentinel.claim-authorization.routes.claim-auth-test[0].allowed-values[1]",
                () -> "finance");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));
        wireMock.stubFor(get(urlPathMatching("/api/claim-auth/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));
    }

    @AfterEach
    void resetEnabled() {
        properties.setEnabled(true);
    }

    private WebTestClient withClaim(String claim, String value) {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .jwt(jwt -> jwt.claim(claim, value)));
    }

    private WebTestClient noClaim() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void matchingClaimValue_returns200() {
        withClaim("department", "engineering").get().uri("/api/claim-auth/resource")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void wrongClaimValue_returns403() {
        withClaim("department", "marketing").get().uri("/api/claim-auth/resource")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void missingRequiredClaim_returns403() {
        noClaim().get().uri("/api/claim-auth/resource")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void filterDisabled_wrongClaimPasses() {
        properties.setEnabled(false);

        withClaim("department", "marketing").get().uri("/api/claim-auth/resource")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void wrongClaim_403Body_hasExpectedFields() {
        withClaim("department", "sales").get().uri("/api/claim-auth/resource")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body.get("error")).isEqualTo("JWT claim authorization failed");
                    assertThat(((Number) body.get("status")).intValue()).isEqualTo(403);
                });
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void secondAllowedValue_finance_returns200() {
        withClaim("department", "finance").get().uri("/api/claim-auth/resource")
                .exchange()
                .expectStatus().isOk();
    }
}
