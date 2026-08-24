package com.sentinelgateway.gateway.tracing;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Phase 10: W3C Trace Context header propagation.
 *
 * Each test fires a real request through the Spring Cloud Gateway stack.
 * WireMock captures what headers the upstream actually received, allowing us
 * to assert that traceparent / tracestate / baggage are set correctly.
 *
 * Acceptance criteria:
 *   1. No traceparent from client  → gateway injects a new, valid traceparent
 *   2. Valid traceparent from client → gateway preserves trace-id, generates new parent-id
 *   3. Invalid traceparent from client → gateway replaces with a fresh traceparent
 *   4. baggage header always contains request-id=<value>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class TracingFilterTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String ISSUER = "http://test-tracing-issuer/realms/sentinel";

    // Known valid traceparent used in Test 2
    private static final String KNOWN_TRACE_ID   = "aabbccdd11223344aabbccdd11223344";
    private static final String KNOWN_PARENT_ID  = "0011223344556677";
    private static final String KNOWN_TRACEPARENT =
            "00-" + KNOWN_TRACE_ID + "-" + KNOWN_PARENT_ID + "-01";

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("tracing-test-key").generate();

        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        JWKSet jwks = new JWKSet(rsaKey.toPublicJWK());
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwks.toString())));

        // Upstream stub: echo-style — returns 200 for any /api/trace/* request
        wireMock.stubFor(any(urlPathMatching("/api/trace/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        String base = "http://localhost:" + wireMock.port();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> ISSUER);

        // Tracing enabled, propagate-existing enabled (defaults; set explicitly)
        registry.add("sentinel.tracing.enabled",           () -> "true");
        registry.add("sentinel.tracing.propagate-existing", () -> "true");

        // Single route for tracing tests
        registry.add("sentinel.gateway.routes[0].route-id",    () -> "trace-test-service");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/trace/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    @BeforeEach
    void resetWireMockEvents() {
        wireMock.resetRequests();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String jwt() throws Exception {
        var signer = new RSASSASigner(rsaKey);
        var claims = new JWTClaimsSet.Builder()
                .subject("tracing-test-user")
                .issuer(ISSUER)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", "openid profile")
                .claim("tenant_id", "tenant-trace")
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    /** Returns the first upstream ServeEvent (i.e. the request that WireMock received). */
    private ServeEvent firstEvent() {
        List<ServeEvent> events = wireMock.getAllServeEvents();
        assertThat(events).as("Expected at least one request to reach WireMock upstream").isNotEmpty();
        return events.get(0);
    }

    // ── Test 1: no traceparent → gateway injects new ──────────────────────────

    @Test
    void noTraceparent_gatewayInjectsNew() throws Exception {
        webTestClient.get().uri("/api/trace/check")
                .header("Authorization", "Bearer " + jwt())
                .exchange()
                .expectStatus().isOk();

        String tp = firstEvent().getRequest().getHeader("traceparent");
        assertThat(tp)
                .as("Gateway must inject a valid W3C traceparent when none is provided")
                .isNotNull()
                .matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$");
    }

    // ── Test 2: valid traceparent → trace-id preserved, parent-id replaced ────

    @Test
    void validTraceparent_isForwardedWithNewParentId() throws Exception {
        webTestClient.get().uri("/api/trace/check")
                .header("Authorization", "Bearer " + jwt())
                .header("traceparent", KNOWN_TRACEPARENT)
                .exchange()
                .expectStatus().isOk();

        String tp = firstEvent().getRequest().getHeader("traceparent");
        assertThat(tp)
                .as("Gateway must preserve the client trace-id when traceparent is valid")
                .isNotNull()
                .startsWith("00-" + KNOWN_TRACE_ID + "-");

        // parent-id must be different (gateway generates a new hop segment)
        String[] parts = tp.split("-");
        assertThat(parts[2])
                .as("Gateway must generate a new parent-id for this hop")
                .isNotEqualTo(KNOWN_PARENT_ID);
    }

    // ── Test 3: invalid traceparent → gateway replaces with new ───────────────

    @Test
    void invalidTraceparent_gatewayReplacesWithNew() throws Exception {
        webTestClient.get().uri("/api/trace/check")
                .header("Authorization", "Bearer " + jwt())
                .header("traceparent", "invalid-garbage")
                .exchange()
                .expectStatus().isOk();

        String tp = firstEvent().getRequest().getHeader("traceparent");
        assertThat(tp)
                .as("Gateway must replace an invalid traceparent with a properly formatted new one")
                .isNotNull()
                .matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$");

        // Must not contain 'invalid' or 'garbage'
        assertThat(tp).doesNotContain("invalid").doesNotContain("garbage");
    }

    // ── Test 4: baggage header contains request-id ────────────────────────────

    @Test
    void baggageHeader_containsRequestId() throws Exception {
        webTestClient.get().uri("/api/trace/check")
                .header("Authorization", "Bearer " + jwt())
                .exchange()
                .expectStatus().isOk();

        String baggage = firstEvent().getRequest().getHeader("baggage");
        assertThat(baggage)
                .as("Upstream must receive a baggage header containing request-id=<value>")
                .isNotNull()
                .contains("request-id=");
    }
}
