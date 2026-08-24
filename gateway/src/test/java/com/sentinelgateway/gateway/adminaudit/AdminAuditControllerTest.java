package com.sentinelgateway.gateway.adminaudit;

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

import java.time.Instant;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AdminAuditController} (Phase 31: Admin Operation Audit Trail).
 *
 * Scenarios:
 *   1. GET /admin/audit-log returns expected fields
 *   2. Seeded records appear in the log
 *   3. limit parameter restricts returned records
 *   4. POST /admin/audit-log/clear empties the log
 *   5. Requires ROLE_ADMIN
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AdminAuditControllerTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AdminAuditRegistry registry;

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
        registry.clear();
    }

    private WebTestClient adminClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private WebTestClient userClient() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    private AdminAuditRecord rec(String path) {
        return new AdminAuditRecord(Instant.now(), "test-admin", "GET", path, 200);
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void getAuditLog_admin_returnsExpectedFields() {
        adminClient().get().uri("/admin/audit-log")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("total", "returned", "records");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void seededRecords_appearsInLog() {
        registry.record(rec("/admin/sessions"));
        registry.record(rec("/admin/uptime"));

        adminClient().get().uri("/admin/audit-log")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("total")).intValue()).isGreaterThanOrEqualTo(2);
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void limitParameter_restrictedResults() {
        for (int i = 0; i < 10; i++) {
            registry.record(rec("/admin/req-" + i));
        }

        adminClient().get().uri("/admin/audit-log?limit=3")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("returned")).intValue()).isEqualTo(3);
                });
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void clearLog_emptiesRegistry() {
        registry.record(rec("/admin/sessions"));

        adminClient().post().uri("/admin/audit-log/clear")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(((Number) body.get("remaining")).intValue()).isZero();
                });

        assertThat(registry.size()).isZero();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void getAuditLog_userRole_returns403() {
        userClient().get().uri("/admin/audit-log")
                .exchange()
                .expectStatus().isForbidden();
    }
}
