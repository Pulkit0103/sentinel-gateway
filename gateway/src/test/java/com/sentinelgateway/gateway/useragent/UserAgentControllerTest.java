package com.sentinelgateway.gateway.useragent;

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
 * Integration + unit tests for User-Agent distribution (Phase 48).
 *
 * Scenarios:
 *   1. GET /admin/user-agent-stats returns expected fields
 *   2. Seeded categories appear in snapshot
 *   3. POST /admin/user-agent-stats/reset clears counters
 *   4. Requires ROLE_ADMIN
 *   5. UserAgentRegistry.categorise classifies correctly
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class UserAgentControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private UserAgentRegistry registry;

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
        adminClient().get().uri("/admin/user-agent-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("enabled", "total", "categories");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void seededCategories_appearInSnapshot() {
        registry.record("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0");
        registry.record("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/119.0");
        registry.record("curl/7.88.1");
        registry.record("Googlebot/2.1 (+http://www.google.com/bot.html)");

        adminClient().get().uri("/admin/user-agent-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("total")).longValue()).isEqualTo(4L);
                    Map<String, Object> categories = (Map<String, Object>) body.get("categories");
                    assertThat(((Number) categories.get("browser")).longValue()).isEqualTo(2L);
                    assertThat(((Number) categories.get("service")).longValue()).isEqualTo(1L);
                    assertThat(((Number) categories.get("bot")).longValue()).isEqualTo(1L);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void resetEndpoint_clearsCounters() {
        registry.record("curl/7.88.1");

        adminClient().post().uri("/admin/user-agent-stats/reset")
                .exchange()
                .expectStatus().isOk();

        assertThat(((Number) registry.snapshot().get("total")).longValue()).isZero();
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void getStats_userRole_returns403() {
        userClient().get().uri("/admin/user-agent-stats")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void categorise_classifiesCorrectly() {
        assertThat(UserAgentRegistry.categorise(null)).isEqualTo("unknown");
        assertThat(UserAgentRegistry.categorise("")).isEqualTo("unknown");
        assertThat(UserAgentRegistry.categorise("Googlebot/2.1")).isEqualTo("bot");
        assertThat(UserAgentRegistry.categorise("Mozilla/5.0 (Linux; Android 13) AppleWebKit")).isEqualTo("mobile");
        assertThat(UserAgentRegistry.categorise("Mozilla/5.0 (Windows NT 10.0) Chrome/120.0 Safari/537.36"))
                .isEqualTo("browser");
        assertThat(UserAgentRegistry.categorise("curl/7.88.1")).isEqualTo("service");
        assertThat(UserAgentRegistry.categorise("python-requests/2.31")).isEqualTo("service");
        assertThat(UserAgentRegistry.categorise("MyCustomService/1.0")).isEqualTo("service");
    }
}
