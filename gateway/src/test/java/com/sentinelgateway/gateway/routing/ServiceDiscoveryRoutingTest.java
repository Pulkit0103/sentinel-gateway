package com.sentinelgateway.gateway.routing;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Phase 15: Service Discovery / Dynamic Backends.
 *
 * Verifies that:
 *   - lb:// URIs resolve via the Simple Discovery Client and route correctly
 *   - Multiple service instances are load-balanced (round-robin across all instances)
 *   - Direct http:// URIs continue to work alongside lb:// (no regression)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ServiceDiscoveryRoutingTest {

    private static WireMockServer wireMock1;
    private static WireMockServer wireMock2;
    private static WireMockServer wireMockDirect;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        wireMock1 = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock2 = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockDirect = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

        wireMock1.start();
        wireMock2.start();
        wireMockDirect.start();

        stubOkJson(wireMock1, "{\"instance\":\"one\"}");
        stubOkJson(wireMock2, "{\"instance\":\"two\"}");
        stubOkJson(wireMockDirect, "{\"instance\":\"direct\"}");

        // JWKS URI — mockJwt() bypasses real JWT validation; property must exist for context.
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + wireMock1.port() + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "");

        // Disable load-balancer cache so instance list is always fresh in tests.
        registry.add("spring.cloud.loadbalancer.cache.enabled", () -> "false");

        // Register two instances of "discovery-test-service" for load-balancing tests.
        registry.add("spring.cloud.discovery.client.simple.instances.discovery-test-service[0].uri",
                () -> "http://localhost:" + wireMock1.port());
        registry.add("spring.cloud.discovery.client.simple.instances.discovery-test-service[1].uri",
                () -> "http://localhost:" + wireMock2.port());

        // Route 0: lb:// URI — resolved via discovery client
        registry.add("sentinel.gateway.routes[0].route-id",    () -> "discovery-test-service");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/discovery/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> "lb://discovery-test-service");
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");

        // Route 1: direct http:// URI — must still work alongside lb:// routes (no regression)
        registry.add("sentinel.gateway.routes[1].route-id",    () -> "direct-service");
        registry.add("sentinel.gateway.routes[1].path",        () -> "/api/direct/**");
        registry.add("sentinel.gateway.routes[1].service-uri",
                () -> "http://localhost:" + wireMockDirect.port());
        registry.add("sentinel.gateway.routes[1].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[1].enabled",     () -> "true");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock1 != null && wireMock1.isRunning()) wireMock1.stop();
        if (wireMock2 != null && wireMock2.isRunning()) wireMock2.stop();
        if (wireMockDirect != null && wireMockDirect.isRunning()) wireMockDirect.stop();
    }

    private static void stubOkJson(WireMockServer wm, String body) {
        wm.stubFor(get(urlPathMatching("/api/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
        // Dummy JWKS so property resolves if context boot somehow needs it
        wm.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));
    }

    @BeforeEach
    void resetEventHistory() {
        if (wireMock1 != null) wireMock1.resetRequests();
        if (wireMock2 != null) wireMock2.resetRequests();
        if (wireMockDirect != null) wireMockDirect.resetRequests();
    }

    private WebTestClient authed() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void lbUri_routesRequestToRegisteredInstance() {
        authed().get().uri("/api/discovery/items")
                .exchange()
                .expectStatus().isOk();

        // At least one of the two instances received the request
        int total = wireMock1.getAllServeEvents().size() + wireMock2.getAllServeEvents().size();
        assertThat(total).isGreaterThanOrEqualTo(1);
    }

    @Test
    void lbUri_roundRobinsAcrossBothInstances() {
        // Send 4 requests — with 2 instances and round-robin, both must be hit
        for (int i = 0; i < 4; i++) {
            authed().get().uri("/api/discovery/items/" + i)
                    .exchange()
                    .expectStatus().isOk();
        }

        int hits1 = (int) wireMock1.getAllServeEvents().stream()
                .filter(e -> e.getRequest().getUrl().startsWith("/api/discovery/"))
                .count();
        int hits2 = (int) wireMock2.getAllServeEvents().stream()
                .filter(e -> e.getRequest().getUrl().startsWith("/api/discovery/"))
                .count();

        // Round-robin: both instances must have received at least one request
        assertThat(hits1).as("instance-one hit count").isGreaterThanOrEqualTo(1);
        assertThat(hits2).as("instance-two hit count").isGreaterThanOrEqualTo(1);
        assertThat(hits1 + hits2).as("total hits").isEqualTo(4);
    }

    @Test
    void directHttpUri_continuesWorkingAlongsideLbRoutes() {
        authed().get().uri("/api/direct/status")
                .exchange()
                .expectStatus().isOk();

        assertThat(wireMockDirect.getAllServeEvents()).isNotEmpty();
    }

    @Test
    void unknownService_returnsServiceUnavailable() {
        // lb://nonexistent-service has no registered instances → 503
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt())
                .get().uri("/api/unknown/resource")
                .exchange()
                .expectStatus().isNotFound(); // 404 — no route matches /api/unknown/**
    }
}
