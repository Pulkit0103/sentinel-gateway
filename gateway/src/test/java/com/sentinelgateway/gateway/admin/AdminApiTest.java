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
 * Integration tests for Admin API — Phase 1 (routes/policies/keys list + key lifecycle)
 * and Phase 2 (route CRUD, policy CRUD, blocklist management).
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

    // ── Phase 1: Route List ───────────────────────────────────────────────────

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

    // ── Phase 2: Route CRUD ───────────────────────────────────────────────────

    @Test
    void adminUser_canCreateAndRetrieveRoute() {
        String routeId = "test-route-" + System.currentTimeMillis();
        var request = Map.of(
                "routeId", routeId,
                "path", "/api/test/**",
                "serviceUri", "http://localhost:9090",
                "methods", List.of("GET", "POST"),
                "enabled", true,
                "rateLimitPolicy", "DEFAULT"
        );

        adminClient().post().uri("/admin/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body.get("routeId")).isEqualTo(routeId);
                    assertThat(body.get("path")).isEqualTo("/api/test/**");
                    assertThat(body.get("enabled")).isEqualTo(true);
                });

        adminClient().get().uri("/admin/routes/" + routeId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body.get("routeId")).isEqualTo(routeId));
    }

    @Test
    void adminUser_canDisableAndEnableRoute() {
        String routeId = "toggle-route-" + System.currentTimeMillis();
        adminClient().post().uri("/admin/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("routeId", routeId, "path", "/api/toggle/**",
                        "serviceUri", "http://localhost:9090"))
                .exchange()
                .expectStatus().isCreated();

        adminClient().post().uri("/admin/routes/" + routeId + "/disable")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body.get("enabled")).isEqualTo(false));

        adminClient().post().uri("/admin/routes/" + routeId + "/enable")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body.get("enabled")).isEqualTo(true));
    }

    @Test
    void adminUser_canDeleteRoute() {
        String routeId = "delete-route-" + System.currentTimeMillis();
        adminClient().post().uri("/admin/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("routeId", routeId, "path", "/api/del/**",
                        "serviceUri", "http://localhost:9090"))
                .exchange()
                .expectStatus().isCreated();

        adminClient().delete().uri("/admin/routes/" + routeId)
                .exchange()
                .expectStatus().isNoContent();

        adminClient().get().uri("/admin/routes/" + routeId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createRoute_conflict_returns409() {
        String routeId = "conflict-route-" + System.currentTimeMillis();
        var request = Map.of("routeId", routeId, "path", "/api/conflict/**",
                "serviceUri", "http://localhost:9090");

        adminClient().post().uri("/admin/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated();

        adminClient().post().uri("/admin/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    // ── Phase 2: Policy CRUD ──────────────────────────────────────────────────

    @Test
    void adminUser_canListPolicies() {
        adminClient().get().uri("/admin/policies")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .value(list -> assertThat(list).isNotEmpty());
    }

    @Test
    void adminUser_canCreateAndUpdatePolicy() {
        String routeId = "policy-route-" + System.currentTimeMillis();
        var createRequest = Map.of(
                "routeId", routeId,
                "requireMfa", false,
                "requestSigningRequired", false,
                "allowedMethods", List.of("GET"),
                "rateLimitPolicy", "USER"
        );

        String policyId = adminClient().post().uri("/admin/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody()
                .get("policyId")
                .toString();

        adminClient().put().uri("/admin/policies/" + policyId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("routeId", routeId, "requireMfa", true, "rateLimitPolicy", "PREMIUM"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body.get("requireMfa")).isEqualTo(true);
                    assertThat(body.get("rateLimitPolicy")).isEqualTo("PREMIUM");
                });
    }

    @Test
    void nonAdminUser_forbiddenOnPolicies() {
        userClient().get().uri("/admin/policies")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Phase 1: API Keys ─────────────────────────────────────────────────────

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

        adminClient().delete().uri("/admin/api-keys/" + id.longValue())
                .exchange()
                .expectStatus().isNoContent();

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

    // ── Phase 2: Blocklist ────────────────────────────────────────────────────

    @Test
    void adminUser_canListBlocklist() {
        adminClient().get().uri("/admin/blocklist")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void adminUser_canBlockAndUnblockIp() {
        String ip = "10.0.0.1";

        adminClient().post().uri("/admin/blocklist")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("ip", ip))
                .exchange()
                .expectStatus().isCreated();

        adminClient().get().uri("/admin/blocklist")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(String.class)
                .value(ips -> assertThat(ips).contains(ip));

        adminClient().delete().uri("/admin/blocklist/" + ip)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void blockIp_invalidIp_returns400() {
        adminClient().post().uri("/admin/blocklist")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("ip", "not-an-ip"))
                .exchange()
                .expectStatus().isBadRequest();
    }
}
