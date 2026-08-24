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
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JwtAudienceFilter} (Phase 22: JWT Audience Validation).
 *
 * {@link JwtAudienceProperties} is injected and configured in-test for toggling.
 * {@code mockJwt()} allows injecting JWTs with specific {@code aud} claims.
 *
 * Scenarios:
 *   1. Audience validation disabled → any JWT passes (200)
 *   2. JWT with matching audience → 200
 *   3. JWT with wrong audience → 401
 *   4. JWT with no audience claim → 401
 *   5. JWT with one of multiple allowed audiences → 200
 *   6. 401 body has expected JSON fields
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class JwtAudienceFilterTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtAudienceProperties audienceProperties;

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

        registry.add("sentinel.gateway.routes[0].route-id",    () -> "aud-test-route");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/aud-test/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
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
        wireMock.stubFor(get(urlPathMatching("/api/aud-test/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));
    }

    @AfterEach
    void resetProperties() {
        audienceProperties.setRequiredAudiences(List.of());
    }

    private WebTestClient withAudience(String... audiences) {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .jwt(jwt -> jwt.claim(JwtClaimNames.AUD, List.of(audiences))));
    }

    private WebTestClient withNoAudience() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void audienceValidationDisabled_anyJwtPasses() {
        audienceProperties.setRequiredAudiences(List.of());

        withNoAudience().get().uri("/api/aud-test/resource")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void jwtWithMatchingAudience_returns200() {
        audienceProperties.setRequiredAudiences(List.of("sentinel-gateway"));

        withAudience("sentinel-gateway").get().uri("/api/aud-test/resource")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void jwtWithWrongAudience_returns401() {
        audienceProperties.setRequiredAudiences(List.of("sentinel-gateway"));

        withAudience("wrong-service").get().uri("/api/aud-test/resource")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void jwtWithNoAudienceClaim_returns401() {
        audienceProperties.setRequiredAudiences(List.of("sentinel-gateway"));

        withNoAudience().get().uri("/api/aud-test/resource")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void jwtWithOneOfMultipleAllowedAudiences_returns200() {
        audienceProperties.setRequiredAudiences(List.of("sentinel-gateway", "another-service"));

        withAudience("another-service").get().uri("/api/aud-test/resource")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void audienceMismatch_401Body_hasExpectedFields() {
        audienceProperties.setRequiredAudiences(List.of("sentinel-gateway"));

        withAudience("bad-aud").get().uri("/api/aud-test/resource")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body.get("error")).isEqualTo("Invalid JWT audience");
                    assertThat(((Number) body.get("status")).intValue()).isEqualTo(401);
                });
    }
}
