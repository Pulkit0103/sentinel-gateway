package com.sentinelgateway.gateway.routeerrorrate;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
 * Integration + unit tests for per-route error rate sliding windows (Phase 65).
 *
 * Scenarios:
 *   1. GET /admin/route-error-rate returns expected fields
 *   2. Seeded mix of success and error calls produces correct route entry
 *   3. POST /admin/route-error-rate/reset clears counters
 *   4. Requires ROLE_ADMIN
 *   5. pathBucket extracts 2-segment route key correctly
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RouteErrorRateControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteErrorRateRegistry registry;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry reg) {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));

        String base = "http://localhost:" + wireMock.port();
        reg.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> base + "/jwks");
        reg.add("sentinel.security.jwt.issuer", () -> "");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    @AfterEach
    void clearRegistry() {
        registry.reset();
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
    void getStats_returnsExpectedFields() {
        adminClient().get().uri("/admin/route-error-rate")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("enabled", "routeCount", "routes");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void seededCalls_produceCorrectRouteWindowEntry() {
        registry.record("/api/orders", false);
        registry.record("/api/orders", false);
        registry.record("/api/orders", true);   // 1 error out of 3

        adminClient().get().uri("/admin/route-error-rate")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("routeCount")).intValue()).isEqualTo(1);
                    Map<String, Object> routes = (Map<String, Object>) body.get("routes");
                    assertThat(routes).containsKey("/api/orders");
                    Map<String, Object> windows = (Map<String, Object>) routes.get("/api/orders");
                    Map<String, Object> w60 = (Map<String, Object>) windows.get("60s");
                    assertThat(((Number) w60.get("total")).longValue()).isEqualTo(3L);
                    assertThat(((Number) w60.get("errors")).longValue()).isEqualTo(1L);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void resetEndpoint_clearsRoutes() {
        registry.record("/api/users", true);

        adminClient().post().uri("/admin/route-error-rate/reset")
                .exchange()
                .expectStatus().isOk();

        assertThat(registry.routeCount()).isZero();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getStats_userRole_returns403() {
        userClient().get().uri("/admin/route-error-rate")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void pathBucket_extractsTwoSegments() {
        assertThat(RouteErrorRateFilter.pathBucket("/api/users/123")).isEqualTo("/api/users");
        assertThat(RouteErrorRateFilter.pathBucket("/api/orders/456/items")).isEqualTo("/api/orders");
        assertThat(RouteErrorRateFilter.pathBucket("/api")).isEqualTo("/api");
        assertThat(RouteErrorRateFilter.pathBucket("/")).isEqualTo("/");
    }
}
