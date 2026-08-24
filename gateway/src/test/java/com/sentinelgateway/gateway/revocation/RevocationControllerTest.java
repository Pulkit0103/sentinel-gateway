package com.sentinelgateway.gateway.revocation;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RevocationController}.
 *
 * Uses a full Spring Boot context with mocked {@link TokenRevocationService}
 * to avoid requiring a live Redis instance.
 *
 * Scenarios:
 *   POST /admin/revoke with future expiresAt  → 204 No Content
 *   POST /admin/revoke with past expiresAt    → 400 Bad Request (already expired)
 *   POST /admin/revoke without admin role     → 403 Forbidden
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RevocationControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TokenRevocationService mockRevocationService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + wireMock.port() + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
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

    @Test
    void revokeToken_futureExpiry_returns204() {
        when(mockRevocationService.revokeToken(anyString(), any()))
                .thenReturn(Mono.empty());

        String expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        String body = """
                {"jti":"test-jti-001","expiresAt":"%s"}
                """.formatted(expiresAt);

        adminClient().post().uri("/admin/revoke")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void revokeToken_pastExpiry_returns400() {
        String expiresAt = Instant.now().minus(10, ChronoUnit.MINUTES).toString();
        String body = """
                {"jti":"test-jti-002","expiresAt":"%s"}
                """.formatted(expiresAt);

        adminClient().post().uri("/admin/revoke")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void revokeToken_withoutAdminRole_returns403() {
        String expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        String body = """
                {"jti":"test-jti-003","expiresAt":"%s"}
                """.formatted(expiresAt);

        userClient().post().uri("/admin/revoke")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isForbidden();
    }
}
