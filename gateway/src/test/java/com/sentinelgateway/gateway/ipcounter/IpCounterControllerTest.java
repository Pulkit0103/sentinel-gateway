package com.sentinelgateway.gateway.ipcounter;

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

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration + unit tests for per-IP request count tracking (Phase 49).
 *
 * Scenarios:
 *   1. GET /admin/ip-stats returns expected fields
 *   2. Seeded IPs appear in topIps sorted by count descending
 *   3. POST /admin/ip-stats/reset clears counters
 *   4. Requires ROLE_ADMIN
 *   5. IpCounterFilter.extractClientIp prefers X-Forwarded-For first hop
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class IpCounterControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private IpCounterRegistry registry;

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
        adminClient().get().uri("/admin/ip-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("enabled", "topN", "total", "uniqueIps", "topIps");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void seededIps_appearSortedByCountDescending() {
        registry.record("10.0.0.1");
        registry.record("10.0.0.1");
        registry.record("10.0.0.1");
        registry.record("10.0.0.2");
        registry.record("10.0.0.2");
        registry.record("10.0.0.3");

        adminClient().get().uri("/admin/ip-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("total")).longValue()).isEqualTo(6L);
                    assertThat(((Number) body.get("uniqueIps")).intValue()).isEqualTo(3);
                    List<Map<String, Object>> topIps = (List<Map<String, Object>>) body.get("topIps");
                    assertThat(topIps).hasSize(3);
                    assertThat(topIps.get(0).get("ip")).isEqualTo("10.0.0.1");
                    assertThat(((Number) topIps.get(0).get("requests")).longValue()).isEqualTo(3L);
                    assertThat(topIps.get(1).get("ip")).isEqualTo("10.0.0.2");
                    assertThat(topIps.get(2).get("ip")).isEqualTo("10.0.0.3");
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void resetEndpoint_clearsCounters() {
        registry.record("10.0.0.1");

        adminClient().post().uri("/admin/ip-stats/reset")
                .exchange()
                .expectStatus().isOk();

        assertThat(registry.getTotal()).isZero();
        assertThat(registry.getUniqueIpCount()).isZero();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getStats_userRole_returns403() {
        userClient().get().uri("/admin/ip-stats")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void extractClientIp_prefersXForwardedForFirstHop() {
        // Use MockServerHttpRequest to build a synthetic exchange with XFF header
        org.springframework.mock.http.server.reactive.MockServerHttpRequest request =
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                        .get("/api/test")
                        .header("X-Forwarded-For", "203.0.113.5, 10.0.0.1, 192.168.1.1")
                        .build();
        org.springframework.mock.web.server.MockServerWebExchange exchange =
                org.springframework.mock.web.server.MockServerWebExchange.from(request);

        assertThat(IpCounterFilter.extractClientIp(exchange)).isEqualTo("203.0.113.5");
    }
}
