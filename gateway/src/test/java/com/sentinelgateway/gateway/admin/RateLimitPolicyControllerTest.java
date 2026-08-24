package com.sentinelgateway.gateway.admin;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Phase 14: Rate Limit Policy Admin API.
 *
 * Verifies that GET /admin/rate-limit/policies returns the configured policies
 * and that the endpoint is properly secured (401 without auth).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RateLimitPolicyControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

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

    // ── Test 1: GET /admin/rate-limit/policies returns configured policies ──────

    @Test
    void getPolicies_returnsConfiguredPolicies() {
        adminClient().get().uri("/admin/rate-limit/policies")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    // Test application.yml configures ANONYMOUS, USER, PREMIUM, DEFAULT
                    assertThat(body).isNotEmpty();
                    assertThat(body).containsKey("ANONYMOUS");
                    assertThat(body).containsKey("DEFAULT");

                    // Verify structure: each policy has requestsPerMinute
                    @SuppressWarnings("unchecked")
                    Map<String, Object> anonymous = (Map<String, Object>) body.get("ANONYMOUS");
                    assertThat(anonymous).containsKey("requestsPerMinute");
                    int rpm = ((Number) anonymous.get("requestsPerMinute")).intValue();
                    assertThat(rpm).isEqualTo(100); // as configured in test application.yml
                });
    }

    // ── Test 2: Unauthenticated request returns 401 ───────────────────────────

    @Test
    void getPolicies_requiresAuthentication_returns401() {
        webTestClient.get().uri("/admin/rate-limit/policies")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
