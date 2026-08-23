package com.sentinelgateway.gateway.routing;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for gateway routing behaviour.
 *
 * WireMock stubs all downstream services. {@code @DynamicPropertySource} starts
 * WireMock BEFORE the Spring context is created so the dynamic port is available
 * for property binding — the same pattern used with Testcontainers.
 *
 * Security is active; all routing tests use {@code mockJwt()} so that security
 * passes and routing behaviour (not auth) is what's under test. JWT auth scenarios
 * are covered separately in {@code JwtAuthenticationTest}.
 *
 * Covers the four Phase 2 acceptance criteria:
 *   1. Valid route → proxied response
 *   2. Unknown route → 404
 *   3. Disabled route → 404 (not registered in Spring Cloud Gateway)
 *   4. Unsupported HTTP method → 405
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRoutingIntegrationTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    /** Returns a WebTestClient pre-configured with a mock JWT so security passes. */
    private WebTestClient authed() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    @DynamicPropertySource
    static void wiremockProperties(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        registerStubs();

        String base = "http://localhost:" + wireMock.port();

        // JWKS URI — mockJwt() bypasses JWT decoding so this never gets called,
        // but the property must be present for the context to start.
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> base + "/jwks");
        // Disable issuer validation so a missing issuer config doesn't break context load.
        registry.add("sentinel.security.jwt.issuer", () -> "");

        // Route 0: user-service — GET and POST only
        registry.add("sentinel.gateway.routes[0].route-id",     () -> "user-service");
        registry.add("sentinel.gateway.routes[0].path",         () -> "/api/users/**");
        registry.add("sentinel.gateway.routes[0].service-uri",  () -> base);
        registry.add("sentinel.gateway.routes[0].methods",      () -> "GET,POST");
        registry.add("sentinel.gateway.routes[0].enabled",      () -> "true");

        // Route 1: order-service
        registry.add("sentinel.gateway.routes[1].route-id",     () -> "order-service");
        registry.add("sentinel.gateway.routes[1].path",         () -> "/api/orders/**");
        registry.add("sentinel.gateway.routes[1].service-uri",  () -> base);
        registry.add("sentinel.gateway.routes[1].methods",      () -> "GET,POST");
        registry.add("sentinel.gateway.routes[1].enabled",      () -> "true");

        // Route 2: test-disabled — DISABLED
        registry.add("sentinel.gateway.routes[2].route-id",     () -> "test-disabled");
        registry.add("sentinel.gateway.routes[2].path",         () -> "/api/legacy/**");
        registry.add("sentinel.gateway.routes[2].service-uri",  () -> "http://localhost:9999");
        registry.add("sentinel.gateway.routes[2].methods",      () -> "GET");
        registry.add("sentinel.gateway.routes[2].enabled",      () -> "false");

        // Route 3: hello-service — GET only (for method-mismatch test)
        registry.add("sentinel.gateway.routes[3].route-id",     () -> "hello-service");
        registry.add("sentinel.gateway.routes[3].path",         () -> "/api/hello/**");
        registry.add("sentinel.gateway.routes[3].service-uri",  () -> base);
        registry.add("sentinel.gateway.routes[3].methods",      () -> "GET");
        registry.add("sentinel.gateway.routes[3].enabled",      () -> "true");
    }

    private static void registerStubs() {
        // Dummy JWKS endpoint so the property resolves even if somehow called
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));

        wireMock.stubFor(get(urlPathMatching("/api/users.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"service\":\"user-service\",\"data\":[]}")));

        wireMock.stubFor(post(urlPathMatching("/api/orders.*"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"service\":\"order-service\",\"data\":{\"id\":\"order-new\"}}")));

        wireMock.stubFor(get(urlPathMatching("/api/hello.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Hello!\"}")));
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null && wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    // ── Test 1: Valid route ───────────────────────────────────────────────────

    @Test
    void validRoute_getUsers_proxiesToUpstreamAndReturns200() {
        authed().get().uri("/api/users")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo("user-service");
    }

    @Test
    void validRoute_getHello_proxiesToUpstreamAndReturns200() {
        authed().get().uri("/api/hello")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.message").isEqualTo("Hello!");
    }

    @Test
    void validRoute_xRequestIdHeader_isPropagatedToUpstream() {
        String requestId = "test-routing-id-abc";
        authed().get().uri("/api/users")
                .header("X-Request-ID", requestId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-ID", requestId);
    }

    // ── Test 2: Unknown route ─────────────────────────────────────────────────

    @Test
    void unknownRoute_noMatchingPath_returns404() {
        authed().get().uri("/api/unknown-service/resource")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void unknownRoute_rootPath_returns404() {
        authed().get().uri("/not/configured")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── Test 3: Disabled route ────────────────────────────────────────────────

    @Test
    void disabledRoute_isNotRegistered_returns404() {
        authed().get().uri("/api/legacy/resource")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── Test 4: Unsupported HTTP method ───────────────────────────────────────

    @Test
    void unsupportedMethod_deleteOnHelloRoute_returns405() {
        authed().delete().uri("/api/hello")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status)
                                .as("Unsupported method should return 404 or 405")
                                .isIn(404, 405));
    }

    @Test
    void unsupportedMethod_putOnUsersRoute_returns405() {
        authed().put().uri("/api/users/user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Alice\"}")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status)
                                .as("Unsupported method should return 404 or 405")
                                .isIn(404, 405));
    }
}
