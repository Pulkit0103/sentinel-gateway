package com.sentinelgateway.gateway.admin;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CircuitBreakerStateController} (Phase 21: CB State API).
 *
 * Circuit breakers are lazily created; this test pre-creates one via
 * {@link CircuitBreakerRegistry} to ensure deterministic state.
 *
 * Scenarios:
 *   1. GET /admin/circuit-breakers returns 200 with circuitBreakers list
 *   2. A pre-created CB appears in the list with expected fields
 *   3. GET requires ADMIN role (non-admin → 403)
 *   4. POST /{name}/reset transitions OPEN → CLOSED and reports stateBefore
 *   5. POST /{name}/reset for unknown name returns 404
 *   6. POST reset requires ADMIN (non-admin → 403)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CircuitBreakerStateControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CircuitBreakerRegistry cbRegistry;

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
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void getCircuitBreakers_admin_returns200WithList() {
        adminClient().get().uri("/admin/circuit-breakers")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKey("circuitBreakers");
                    assertThat(body).containsKey("count");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void getCircuitBreakers_preSeededCb_appearsInList() {
        // Pre-create a circuit breaker so it appears in the registry
        cbRegistry.circuitBreaker("test-admin-cb");

        adminClient().get().uri("/admin/circuit-breakers")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> cbs = (List<Map<String, Object>>) body.get("circuitBreakers");
                    boolean found = cbs.stream()
                            .anyMatch(cb -> "test-admin-cb".equals(cb.get("name")));
                    assertThat(found).isTrue();

                    // Verify snapshot fields
                    cbs.stream()
                            .filter(cb -> "test-admin-cb".equals(cb.get("name")))
                            .findFirst()
                            .ifPresent(cb -> {
                                assertThat(cb).containsKeys(
                                        "name", "state", "failureRate", "numberOfBufferedCalls",
                                        "numberOfFailedCalls", "numberOfSuccessfulCalls");
                            });
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void getCircuitBreakers_userRole_returns403() {
        userClient().get().uri("/admin/circuit-breakers")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void resetCircuitBreaker_openCb_transitionsToClosed() {
        CircuitBreaker cb = cbRegistry.circuitBreaker("reset-test-cb");
        cb.transitionToOpenState(); // force OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        adminClient().post().uri("/admin/circuit-breakers/reset-test-cb/reset")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body.get("name")).isEqualTo("reset-test-cb");
                    assertThat(body.get("stateBefore")).isEqualTo("OPEN");
                    assertThat(body.get("stateAfter")).isEqualTo("CLOSED");
                });

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void resetCircuitBreaker_unknownName_returns404() {
        adminClient().post().uri("/admin/circuit-breakers/nonexistent-cb-xyz/reset")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void resetCircuitBreaker_userRole_returns403() {
        userClient().post().uri("/admin/circuit-breakers/any-cb/reset")
                .exchange()
                .expectStatus().isForbidden();
    }
}
