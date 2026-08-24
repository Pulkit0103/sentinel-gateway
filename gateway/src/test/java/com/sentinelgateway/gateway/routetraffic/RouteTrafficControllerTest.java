package com.sentinelgateway.gateway.routetraffic;

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
 * Integration + unit tests for per-route windowed traffic counting (Phase 53).
 *
 * Scenarios:
 *   1. GET /admin/route-traffic returns expected fields
 *   2. Seeded route records appear in snapshot with correct totals
 *   3. POST /admin/route-traffic/reset clears all data
 *   4. Requires ROLE_ADMIN
 *   5. RouteTrafficFilter.pathBucket produces 2-segment buckets
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RouteTrafficControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteTrafficRegistry registry;

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
        adminClient().get().uri("/admin/route-traffic")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("enabled", "routes");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void seededRecords_appearInSnapshotWithCorrectTotal() {
        registry.record("/api/users");
        registry.record("/api/users");
        registry.record("/api/orders");

        adminClient().get().uri("/admin/route-traffic")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    Map<String, Object> routes = (Map<String, Object>) body.get("routes");
                    assertThat(routes).containsKey("/api/users");
                    assertThat(routes).containsKey("/api/orders");
                    Map<String, Object> users = (Map<String, Object>) routes.get("/api/users");
                    assertThat(((Number) users.get("total")).longValue()).isEqualTo(2L);
                    // Records were just inserted so last1m should also be 2
                    assertThat(((Number) users.get("last1m")).longValue()).isEqualTo(2L);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void resetEndpoint_clearsAllData() {
        registry.record("/api/users");

        adminClient().post().uri("/admin/route-traffic/reset")
                .exchange()
                .expectStatus().isOk();

        Map<String, Object> snap = registry.snapshot();
        @SuppressWarnings("unchecked")
        Map<String, Object> routes = (Map<String, Object>) snap.get("routes");
        assertThat(routes).isEmpty();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getStats_userRole_returns403() {
        userClient().get().uri("/admin/route-traffic")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void pathBucket_produces2SegmentBuckets() {
        assertThat(RouteTrafficFilter.pathBucket("/api/users/123")).isEqualTo("/api/users");
        assertThat(RouteTrafficFilter.pathBucket("/api/orders/456/items")).isEqualTo("/api/orders");
        assertThat(RouteTrafficFilter.pathBucket("/api")).isEqualTo("/api");
        assertThat(RouteTrafficFilter.pathBucket("/")).isEqualTo("/");
    }
}
