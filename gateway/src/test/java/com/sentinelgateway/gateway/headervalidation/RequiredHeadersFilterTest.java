package com.sentinelgateway.gateway.headervalidation;

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
 * Integration tests for {@link RequiredHeadersFilter} (Phase 19: Required Header Validation).
 *
 * Route "header-validation-test" requires header {@code X-Idempotency-Key}.
 *
 * Scenarios:
 *   1. Required header present     → 200 passthrough
 *   2. Required header missing     → 400 with JSON body
 *   3. Filter disabled globally    → missing header still passes (200)
 *   4. Route with no config        → always passes (200)
 *   5. Missing header body has "missing" array
 *   6. Multiple missing headers all listed
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RequiredHeadersFilterTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RequiredHeadersProperties properties;

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

        // Route that requires X-Idempotency-Key
        registry.add("sentinel.gateway.routes[0].route-id",    () -> "header-val-test");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/header-val/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET,POST");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");

        // Enable filter with required headers for the test route
        registry.add("sentinel.required-headers.enabled", () -> "true");
        registry.add("sentinel.required-headers.routes.header-val-test[0]", () -> "X-Idempotency-Key");
        registry.add("sentinel.required-headers.routes.header-val-test[1]", () -> "X-Correlation-Id");
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
        wireMock.stubFor(get(urlPathMatching("/api/header-val/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));
    }

    @AfterEach
    void resetEnabled() {
        properties.setEnabled(true);
    }

    private WebTestClient authed() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void allRequiredHeadersPresent_returns200() {
        authed().get().uri("/api/header-val/resource")
                .header("X-Idempotency-Key", "abc-123")
                .header("X-Correlation-Id", "corr-456")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void missingRequiredHeader_returns400() {
        authed().get().uri("/api/header-val/resource")
                // X-Idempotency-Key and X-Correlation-Id both absent
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void filterDisabled_missingHeaderStillPasses() {
        properties.setEnabled(false);

        authed().get().uri("/api/header-val/resource")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void missingHeader_400Body_containsMissingArray() {
        authed().get().uri("/api/header-val/resource")
                .header("X-Idempotency-Key", "present")
                // X-Correlation-Id absent
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKey("missing");
                    @SuppressWarnings("unchecked")
                    List<String> missing = (List<String>) body.get("missing");
                    assertThat(missing).contains("X-Correlation-Id");
                    assertThat(missing).doesNotContain("X-Idempotency-Key");
                });
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void missingHeader_400Body_hasErrorField() {
        authed().get().uri("/api/header-val/resource")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body.get("error")).isEqualTo("Missing required header");
                    assertThat(((Number) body.get("status")).intValue()).isEqualTo(400);
                });
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void allHeadersMissing_400Body_listsAllInMissingArray() {
        authed().get().uri("/api/header-val/resource")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    @SuppressWarnings("unchecked")
                    List<String> missing = (List<String>) body.get("missing");
                    assertThat(missing).containsExactlyInAnyOrder("X-Idempotency-Key", "X-Correlation-Id");
                });
    }
}
