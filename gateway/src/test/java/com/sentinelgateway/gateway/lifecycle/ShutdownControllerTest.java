package com.sentinelgateway.gateway.lifecycle;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
 * Integration tests for {@link ShutdownController} (Phase 17: Graceful Shutdown).
 *
 * {@link ConfigurableApplicationContext} is mocked so that
 * {@code applicationContext.close()} does NOT actually shut down the test JVM.
 *
 * Scenarios:
 *   POST /admin/shutdown with ADMIN role → 200 with status=shutting_down in body
 *   POST /admin/shutdown without auth    → 401 Unauthorized
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ShutdownControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    /** No-op mock so shutdown() call doesn't actually close the test context. */
    @MockBean
    private ShutdownHandler shutdownHandler;

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

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ── Test 1: Admin POST returns 200 with shutdown body ────────────────────

    @Test
    void shutdown_withAdminRole_returns200AndShuttingDownStatus() {
        adminClient().post().uri("/admin/shutdown")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKey("status");
                    assertThat(body.get("status")).isEqualTo("shutting_down");
                    assertThat(body).containsKey("drainTimeoutSeconds");
                    int timeout = ((Number) body.get("drainTimeoutSeconds")).intValue();
                    assertThat(timeout).isEqualTo(30);
                });
    }

    // ── Test 2: Unauthenticated request returns 401 ───────────────────────────

    @Test
    void shutdown_withoutAuth_returns401() {
        webTestClient.post().uri("/admin/shutdown")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
