package com.sentinelgateway.gateway.resilience;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for Phase 16: Resilience — circuit breaker behaviour.
 *
 * Retry is disabled to avoid interference with circuit-breaker failure counting.
 * The circuit breaker is configured with a minimal sliding window (2 calls, 100% failure
 * threshold) so it opens immediately after two connection-level errors.
 *
 * Verifies:
 *   - Repeated upstream connection failures open the circuit
 *   - An open circuit returns 503 without making any upstream call
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT30S")
class CircuitBreakerTest {

    private static WireMockServer wireMock;
    private static final String ROUTE_ID = "cb-test-service";
    private static final String CB_NAME   = ROUTE_ID + "-cb";

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

        // Disable retry — prevents interference with circuit-breaker failure counting
        registry.add("sentinel.resilience.retry.enabled", () -> "false");
        // Enable circuit breaker
        registry.add("sentinel.resilience.circuit-breaker.enabled", () -> "true");

        // Fast-fail circuit breaker: opens after 2 connection-level errors (100% failure rate)
        registry.add("resilience4j.circuitbreaker.instances." + CB_NAME + ".slidingWindowSize",
                () -> "2");
        registry.add("resilience4j.circuitbreaker.instances." + CB_NAME + ".failureRateThreshold",
                () -> "100");
        // Keep the circuit OPEN for the full test run (60s > test duration)
        registry.add("resilience4j.circuitbreaker.instances." + CB_NAME + ".waitDurationInOpenState",
                () -> "60000");
        registry.add("resilience4j.circuitbreaker.instances." + CB_NAME + ".permittedNumberOfCallsInHalfOpenState",
                () -> "1");

        registry.add("sentinel.gateway.routes[0].route-id",    () -> ROUTE_ID);
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/cb/**");
        registry.add("sentinel.gateway.routes[0].service-uri",
                () -> "http://localhost:" + wireMock.port());
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    private WebTestClient authed() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    /**
     * Two connection-level errors fill the sliding window (size=2) at 100% failure rate.
     * The third request must be rejected by the open circuit breaker with 503 — the upstream
     * must not receive any requests after the circuit opens.
     */
    @Test
    void repeatedConnectionErrors_openCircuit_subsequentRequestRejectedWith503() {
        // Phase 1: fill the circuit-breaker window with connection-level failures
        wireMock.stubFor(get(urlPathMatching("/api/cb/target"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        for (int i = 0; i < 2; i++) {
            authed().get().uri("/api/cb/target")
                    .exchange()
                    .expectStatus().is5xxServerError();
        }

        // Phase 2: switch upstream to healthy — but the circuit is OPEN so it shouldn't matter
        wireMock.resetRequests();  // clear journal so we can assert 0 upstream hits below
        wireMock.stubFor(get(urlPathMatching("/api/cb/target"))
                .willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));

        // Open circuit must reject the request immediately (503), without calling upstream
        authed().get().uri("/api/cb/target")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // Upstream must NOT have been contacted — circuit open means no upstream call
        wireMock.verify(0, getRequestedFor(urlPathMatching("/api/cb/target")));
    }
}
