package com.sentinelgateway.gateway.hopcount;

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
 * Integration tests for {@link HopCountFilter} (Phase 44: Hop Count Guard).
 *
 * Scenarios:
 *   1. No X-Forwarded-For → passes (0 hops)
 *   2. Hops within limit → passes
 *   3. Hops exceeding limit → 400 Bad Request
 *   4. Filter disabled → excessive hops pass
 *   5. countHops utility unit test
 *   6. Admin endpoint returns expected fields
 *   7. Distribution records correct hop counts
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class HopCountFilterTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private HopCountProperties properties;

    @Autowired
    private HopCountRegistry registry;

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
    void reset() {
        properties.setEnabled(true);
        properties.setMaxHops(10);
        registry.reset();
    }

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void noForwardedFor_passes() {
        adminClient().get().uri("/admin/hop-stats")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void withinLimit_passes() {
        adminClient().get().uri("/admin/hop-stats")
                .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void exceedingLimit_returns400() {
        properties.setMaxHops(2);

        webTestClient.get().uri("/admin/hop-stats")
                .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2, 10.0.0.3")
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void filterDisabled_excessiveHopsPasses() {
        properties.setEnabled(false);
        properties.setMaxHops(1);

        adminClient().get().uri("/admin/hop-stats")
                .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2, 10.0.0.3")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void countHops_parsesCommaSeparatedIPs() {
        assertThat(HopCountFilter.countHops(null)).isZero();
        assertThat(HopCountFilter.countHops(List.of())).isZero();
        assertThat(HopCountFilter.countHops(List.of("10.0.0.1"))).isEqualTo(1);
        assertThat(HopCountFilter.countHops(List.of("10.0.0.1, 10.0.0.2, 10.0.0.3"))).isEqualTo(3);
        assertThat(HopCountFilter.countHops(List.of("10.0.0.1", "10.0.0.2"))).isEqualTo(2);
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────────

    @Test
    void adminEndpoint_returnsExpectedFields() {
        adminClient().get().uri("/admin/hop-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys(
                            "enabled", "maxHops", "totalProcessed", "totalRejected", "distribution");
                });
    }

    // ── Test 7 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void distribution_recordsHopCounts() {
        properties.setMaxHops(2);

        // 3 hops → rejected
        webTestClient.get().uri("/admin/hop-stats")
                .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2, 10.0.0.3")
                .exchange()
                .expectStatus().isBadRequest();

        Map<String, Object> snap = registry.snapshot();
        assertThat(((Number) snap.get("totalRejected")).longValue()).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        Map<Integer, Object> dist = (Map<Integer, Object>) snap.get("distribution");
        assertThat(dist).containsKey(3);
    }
}
