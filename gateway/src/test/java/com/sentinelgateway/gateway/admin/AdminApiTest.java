package com.sentinelgateway.gateway.admin;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
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
 * Integration tests for Phase 17: Admin API.
 *
 * Verifies:
 *   - ADMIN role: full access to /admin/** endpoints (200/201/204)
 *   - Non-admin (USER role): 403 Forbidden on all /admin/** paths
 *   - Unauthenticated: 401 Unauthorized on all /admin/** paths
 *   - API key lifecycle: create returns rawKey once; revoke marks key inactive
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AdminApiTest {

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

    private WebTestClient userClient() {
        return webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // ── Routes ────────────────────────────────────────────────────────────────

    @Test
    void adminUser_canListRoutes() {
        adminClient().get().uri("/admin/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .value(list -> assertThat(list).isNotEmpty());
    }

    @Test
    void nonAdminUser_forbiddenOnRoutes() {
        userClient().get().uri("/admin/routes")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void unauthenticated_unauthorizedOnRoutes() {
        webTestClient.get().uri("/admin/routes")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── API Keys ──────────────────────────────────────────────────────────────

    @Test
    void adminUser_canListApiKeys() {
        adminClient().get().uri("/admin/api-keys")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class);
    }

    @Test
    void adminUser_canCreateApiKey_rawKeyReturnedOnce() {
        var request = Map.of("clientId", "test-client", "tenantId", "tenant-1", "scopes", "read write");

        adminClient().post().uri("/admin/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKey("rawKey");
                    assertThat((String) body.get("rawKey")).startsWith("sgk_");
                    assertThat(body.get("status")).isEqualTo("ACTIVE");
                    assertThat(body).doesNotContainKey("keyHash");
                });
    }

    @Test
    void adminUser_canRevokeApiKey() {
        // Create first
        Map<String, Object> created = adminClient().post().uri("/admin/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("clientId", "revoke-client", "tenantId", "tenant-1", "scopes", "read"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        Number id = (Number) created.get("id");

        // Revoke
        adminClient().delete().uri("/admin/api-keys/" + id.longValue())
                .exchange()
                .expectStatus().isNoContent();

        // Key should now appear REVOKED in the list
        adminClient().get().uri("/admin/api-keys")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(keys -> {
                    boolean revokedFound = keys.stream()
                            .anyMatch(k -> id.longValue() == ((Number) k.get("id")).longValue()
                                    && "REVOKED".equals(k.get("status")));
                    assertThat(revokedFound).isTrue();
                });
    }

    @Test
    void nonAdminUser_forbiddenOnApiKeys() {
        userClient().get().uri("/admin/api-keys")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void unauthenticated_unauthorizedOnApiKeys() {
        webTestClient.get().uri("/admin/api-keys")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── Policies ──────────────────────────────────────────────────────────────

    @Test
    void adminUser_canListPolicies() {
        adminClient().get().uri("/admin/policies")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .value(list -> assertThat(list).isNotEmpty());
    }

    @Test
    void nonAdminUser_forbiddenOnPolicies() {
        userClient().get().uri("/admin/policies")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void unauthenticated_unauthorizedOnPolicies() {
        webTestClient.get().uri("/admin/policies")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
