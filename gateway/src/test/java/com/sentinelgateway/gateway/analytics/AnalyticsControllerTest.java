package com.sentinelgateway.gateway.analytics;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Phase 3: Analytics & Usage Reporting.
 *
 * {@link AnalyticsService} is mocked so tests verify HTTP layer behaviour
 * without needing a live Redis instance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AnalyticsControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AnalyticsService analyticsService;

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
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    @BeforeEach
    void configureMocks() {
        // Default stub: record() is fire-and-forget
        when(analyticsService.record(any())).thenReturn(Mono.empty());

        // Default stubs for query methods
        AnalyticsSummary summary = new AnalyticsSummary(
                LocalDate.now().toString(),
                100L, 90L, 2L, 3L, 1L, 2L, 2L, 10.0,
                List.of(new RouteStats("user-service", 50L)),
                List.of(new TenantStats("tenant-1", 60L)));

        when(analyticsService.getSummary(any())).thenReturn(Mono.just(summary));

        when(analyticsService.getRouteStats(any()))
                .thenReturn(Flux.just(new RouteStats("user-service", 50L)));

        when(analyticsService.getTenantStats(any()))
                .thenReturn(Flux.just(new TenantStats("tenant-1", 60L)));

        when(analyticsService.getTimeline(anyInt()))
                .thenReturn(Flux.just(new HourlyBucket("2026-08-24-10", 10L, 1L, 0L, 0L)));

        when(analyticsService.getSecurityStats(any()))
                .thenReturn(Mono.just(new SecurityStats(
                        LocalDate.now().toString(), 2L, 3L, 1L, 2L, 5L)));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ── 1. Unauthenticated → 401 ──────────────────────────────────────────────

    @Test
    void summary_withoutAuth_returns401() {
        webTestClient.get().uri("/admin/analytics/summary")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── 2. Summary with admin JWT → 200 ──────────────────────────────────────

    @Test
    void summary_withAdminJwt_returns200WithBody() {
        adminClient().get().uri("/admin/analytics/summary")
                .exchange()
                .expectStatus().isOk()
                .expectBody(AnalyticsSummary.class)
                .value(body -> {
                    assert body != null;
                    assert body.totalRequests() == 100L;
                    assert !body.topRoutes().isEmpty();
                });
    }

    // ── 3. Routes with admin JWT → 200 ───────────────────────────────────────

    @Test
    void routes_withAdminJwt_returns200() {
        adminClient().get().uri("/admin/analytics/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(RouteStats.class)
                .hasSize(1);
    }

    // ── 4. Timeline with admin JWT → 200 ─────────────────────────────────────

    @Test
    void timeline_withAdminJwt_returns200() {
        adminClient().get().uri("/admin/analytics/timeline?hours=6")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(HourlyBucket.class)
                .hasSize(1);
    }

    // ── 5. Security stats with admin JWT → 200 ───────────────────────────────

    @Test
    void security_withAdminJwt_returns200() {
        adminClient().get().uri("/admin/analytics/security")
                .exchange()
                .expectStatus().isOk()
                .expectBody(SecurityStats.class)
                .value(body -> {
                    assert body != null;
                    assert body.blockedWaf() == 2L;
                });
    }
}
