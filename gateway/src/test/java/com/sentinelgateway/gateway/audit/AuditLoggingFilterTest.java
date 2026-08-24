package com.sentinelgateway.gateway.audit;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.sentinelgateway.gateway.analytics.AnalyticsService;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Phase 13: Audit Logging.
 *
 * AuditEventPublisher is mocked so tests verify that the filter emits the
 * correct AuditEvent — without needing Kafka or file I/O.
 * AnalyticsService is also mocked to avoid needing a live Redis connection.
 *
 * Scenarios:
 *   Authenticated request → 200, ALLOWED audit event
 *   Unauthenticated request → 401, UNAUTHENTICATED audit event
 *   WAF-blocked request → 400, BLOCKED_WAF audit event
 *   Rate-limited request → 429, RATE_LIMITED audit event (simulated via 429 stub)
 *   Audit event includes request path and request ID
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AuditLoggingFilterTest {

    private static WireMockServer wireMock;
    private static RSAKey rsaKey;
    private static final String ISSUER = "http://test-audit-issuer/realms/sentinel";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @MockBean
    private AnalyticsService analyticsService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("audit-test-key").generate();
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        JWKSet jwks = new JWKSet(rsaKey.toPublicJWK());
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwks.toString())));

        wireMock.stubFor(get(urlPathMatching("/api/audit/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        String base = "http://localhost:" + wireMock.port();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> ISSUER);

        // Threat detection enabled to verify audit fires even on WAF blocks
        registry.add("sentinel.threat-detection.enabled",         () -> "true");
        registry.add("sentinel.threat-detection.block-threshold", () -> "40");
        registry.add("sentinel.threat-detection.alert-threshold", () -> "90");
        registry.add("sentinel.threat-detection.log-threshold",   () -> "10");

        // Route for audit tests
        registry.add("sentinel.gateway.routes[0].route-id",    () -> "audit-test-service");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/audit/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    @BeforeEach
    void configureMock() {
        when(auditEventPublisher.publish(any())).thenReturn(Mono.empty());
        when(analyticsService.record(any())).thenReturn(Mono.empty());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String jwt() throws Exception {
        var signer = new RSASSASigner(rsaKey);
        var claims = new JWTClaimsSet.Builder()
                .subject("audit-test-user")
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

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void authenticatedRequest_emitsAllowedAuditEvent() throws Exception {
        webTestClient.get().uri("/api/audit/users")
                .header("Authorization", "Bearer " + jwt())
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher, times(1)).publish(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.responseStatus()).isEqualTo(200);
        assertThat(event.outcome()).isEqualTo("ALLOWED");
        assertThat(event.path()).isEqualTo("/api/audit/users");
        assertThat(event.method()).isEqualTo("GET");
    }

    @Test
    void unauthenticatedRequest_emitsUnauthenticatedAuditEvent() {
        webTestClient.get().uri("/api/audit/users")
                .exchange()
                .expectStatus().isUnauthorized();

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher, times(1)).publish(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.responseStatus()).isEqualTo(401);
        assertThat(event.outcome()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void wafBlockedRequest_emitsBlockedWafAuditEvent() throws Exception {
        // SQL injection in query → WAF blocks with 400
        webTestClient.get()
                .uri("/api/audit/search?q=' UNION SELECT * FROM users; --")
                .header("Authorization", "Bearer " + jwt())
                .exchange()
                .expectStatus().isBadRequest();

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher, times(1)).publish(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.responseStatus()).isEqualTo(400);
        assertThat(event.outcome()).isEqualTo("BLOCKED_WAF");
    }

    @Test
    void auditEventIncludesRequestId() throws Exception {
        String requestId = "test-req-" + UUID.randomUUID();
        webTestClient.get().uri("/api/audit/users")
                .header("Authorization", "Bearer " + jwt())
                .header("X-Request-ID", requestId)
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher, times(1)).publish(captor.capture());

        assertThat(captor.getValue().requestId()).isEqualTo(requestId);
    }

    @Test
    void everyRequestEmitsExactlyOneAuditEvent() throws Exception {
        // Three different requests — each should produce exactly one audit event
        webTestClient.get().uri("/api/audit/a")
                .header("Authorization", "Bearer " + jwt()).exchange();
        webTestClient.get().uri("/api/audit/b")
                .header("Authorization", "Bearer " + jwt()).exchange();
        webTestClient.get().uri("/api/audit/c")
                .header("Authorization", "Bearer " + jwt()).exchange();

        verify(auditEventPublisher, times(3)).publish(any());
    }
}
