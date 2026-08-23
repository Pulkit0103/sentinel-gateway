package com.sentinelgateway.gateway.metrics;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Phase 14: Observability.
 *
 * Verifies that:
 *   - /actuator/prometheus is accessible and returns metrics
 *   - gateway.requests.total counter is incremented per request
 *   - gateway.request.duration timer is recorded
 *   - metrics are tagged with the correct route identifier
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ObservabilityTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String ISSUER = "http://test-metrics-issuer/realms/sentinel";

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("metrics-test-key").generate();
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        JWKSet jwks = new JWKSet(rsaKey.toPublicJWK());
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwks.toString())));

        wireMock.stubFor(get(urlPathMatching("/api/metrics-test/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        String base = "http://localhost:" + wireMock.port();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> ISSUER);

        registry.add("sentinel.gateway.routes[0].route-id",    () -> "metrics-test-service");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/metrics-test/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
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
                .subject("metrics-test-user")
                .issuer(ISSUER)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", "openid profile")
                .claim("tenant_id", "tenant-metrics")
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    private String scrapePrometheus() {
        return webTestClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void prometheusEndpoint_isAccessibleWithoutAuthentication() {
        webTestClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN);
    }

    @Test
    void authenticatedRequest_incrementsGatewayRequestsTotal() throws Exception {
        webTestClient.get().uri("/api/metrics-test/users")
                .header("Authorization", "Bearer " + jwt())
                .exchange()
                .expectStatus().isOk();

        String metrics = scrapePrometheus();
        assertThat(metrics).contains("gateway_requests_total");
    }

    @Test
    void authenticatedRequest_recordsRequestDurationTimer() throws Exception {
        webTestClient.get().uri("/api/metrics-test/items")
                .header("Authorization", "Bearer " + jwt())
                .exchange()
                .expectStatus().isOk();

        String metrics = scrapePrometheus();
        assertThat(metrics).contains("gateway_request_duration_seconds");
    }

    @Test
    void metrics_includeRouteTag_withCorrectRouteId() throws Exception {
        webTestClient.get().uri("/api/metrics-test/tagged")
                .header("Authorization", "Bearer " + jwt())
                .exchange()
                .expectStatus().isOk();

        String metrics = scrapePrometheus();
        assertThat(metrics).contains("route=\"metrics-test-service\"");
    }

    @Test
    void unauthenticatedRequest_isAlsoCountedWithCorrectOutcome() {
        webTestClient.get().uri("/api/metrics-test/secure")
                .exchange()
                .expectStatus().isUnauthorized();

        String metrics = scrapePrometheus();
        assertThat(metrics).contains("gateway_requests_total");
        assertThat(metrics).contains("outcome=\"UNAUTHENTICATED\"");
    }
}
