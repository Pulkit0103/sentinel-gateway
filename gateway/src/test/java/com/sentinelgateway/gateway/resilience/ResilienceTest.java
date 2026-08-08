package com.sentinelgateway.gateway.resilience;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
 * Integration tests for Phase 16: Resilience — timeout and retry behaviour.
 *
 * Circuit breaker is disabled for this test class to keep each scenario isolated.
 * Circuit-breaker-specific behaviour is covered in {@link CircuitBreakerTest}.
 *
 * Verifies:
 *   - Response timeout: slow upstream triggers 504 Gateway Timeout
 *   - Retry: GET retried on 5xx; succeeds on 2nd attempt → 200
 *   - No retry on POST: POST 5xx is NOT retried; upstream called exactly once
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT30S")
class ResilienceTest {

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

        // Short global response timeout for the timeout test
        registry.add("spring.cloud.gateway.httpclient.response-timeout", () -> "800ms");
        // Retry: 2 attempts on 5xx, GET/HEAD only, no exception-based retries
        registry.add("sentinel.resilience.retry.enabled",  () -> "true");
        registry.add("sentinel.resilience.retry.attempts", () -> "2");
        // Disable circuit breaker — kept off to prevent state from leaking between scenarios
        registry.add("sentinel.resilience.circuit-breaker.enabled", () -> "false");

        registry.add("sentinel.gateway.routes[0].route-id",    () -> "resilience-test-service");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/resilience/**");
        registry.add("sentinel.gateway.routes[0].service-uri",
                () -> "http://localhost:" + wireMock.port());
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET,POST");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));
    }

    private WebTestClient authed() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    // ── Timeout ───────────────────────────────────────────────────────────────

    @Test
    void slowUpstream_exceedsTimeout_returns504() {
        wireMock.stubFor(get(urlPathMatching("/api/resilience/slow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(2000)   // 2 s > 0.8 s timeout
                        .withBody("{\"slow\":true}")));

        authed().get().uri("/api/resilience/slow")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    // ── Retry ─────────────────────────────────────────────────────────────────

    @Test
    void get_retriedOnServerError_returnsOkOnSecondAttempt() {
        wireMock.stubFor(get(urlPathMatching("/api/resilience/retry-item"))
                .inScenario("get-retry")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("first-done"));

        wireMock.stubFor(get(urlPathMatching("/api/resilience/retry-item"))
                .inScenario("get-retry")
                .whenScenarioStateIs("first-done")
                .willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));

        authed().get().uri("/api/resilience/retry-item")
                .exchange()
                .expectStatus().isOk();

        // Both the initial attempt AND the retry must have hit WireMock
        wireMock.verify(moreThanOrExactly(2), getRequestedFor(urlPathMatching("/api/resilience/retry-item")));
    }

    @Test
    void post_notRetried_returns503OnServerError() {
        wireMock.stubFor(post(urlPathMatching("/api/resilience/no-retry"))
                .willReturn(aResponse().withStatus(503).withBody("{\"error\":\"unavailable\"}")));

        authed().post().uri("/api/resilience/no-retry")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // Exactly ONE POST must have reached WireMock (no retry on POST)
        wireMock.verify(1, postRequestedFor(urlPathMatching("/api/resilience/no-retry")));
    }
}
