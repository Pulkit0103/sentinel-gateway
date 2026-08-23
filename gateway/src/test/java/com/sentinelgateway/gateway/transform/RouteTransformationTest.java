package com.sentinelgateway.gateway.transform;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for strip-prefix and add-request-headers route transformations.
 *
 * Verifies that when a route is configured with strip-prefix=1, the gateway removes
 * the first path segment before forwarding to upstream.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RouteTransformationTest {

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

        // Upstream stub expects the path WITHOUT the /api prefix
        wireMock.stubFor(get(urlPathMatching("/users/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"stripped\":true}")));

        // Stub for add-request-header test
        wireMock.stubFor(get(urlPathMatching("/api/svc/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        String base = "http://localhost:" + wireMock.port();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "");

        // Route with strip-prefix=1: /api/users/1 → upstream /users/1
        registry.add("sentinel.gateway.routes[0].route-id",    () -> "strip-prefix-service");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/users/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
        registry.add("sentinel.gateway.routes[0].strip-prefix", () -> "1");

        // Route with add-request-headers
        registry.add("sentinel.gateway.routes[1].route-id",    () -> "header-add-service");
        registry.add("sentinel.gateway.routes[1].path",        () -> "/api/svc/**");
        registry.add("sentinel.gateway.routes[1].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[1].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[1].enabled",     () -> "true");
        registry.add("sentinel.gateway.routes[1].add-request-headers.X-Service-Key", () -> "secret-key");
        registry.add("sentinel.gateway.routes[1].add-request-headers.X-Region", () -> "us-east-1");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    private WebTestClient authed() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    @Test
    void stripPrefix1_requestToApiUsers_upstreamReceivesUsersPath() {
        authed().get().uri("/api/users/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.stripped").isEqualTo(true);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/users/1")));
    }

    @Test
    void addRequestHeaders_configuredHeaders_areForwardedToUpstream() {
        authed().get().uri("/api/svc/resource")
                .exchange()
                .expectStatus().isOk();

        wireMock.verify(getRequestedFor(urlPathMatching("/api/svc/.*"))
                .withHeader("X-Service-Key", equalTo("secret-key"))
                .withHeader("X-Region", equalTo("us-east-1")));
    }
}
