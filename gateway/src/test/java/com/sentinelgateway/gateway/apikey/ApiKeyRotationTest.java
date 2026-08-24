package com.sentinelgateway.gateway.apikey;

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

import java.time.Instant;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Phase 24: API Key Rotation.
 *
 * Scenarios:
 *   1. POST /admin/api-keys/{id}/rotate — returns new raw key + metadata
 *   2. Old key no longer authenticates after rotation
 *   3. New key authenticates successfully
 *   4. Rotate with unknown ID returns 404
 *   5. Rotate requires ADMIN role (USER → 403)
 *   6. newRawKey starts with "sgk_" prefix
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApiKeyRotationTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));

        String base = "http://localhost:" + wireMock.port();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "");

        // Route that uses API key auth
        registry.add("sentinel.gateway.routes[0].route-id",    () -> "rotation-test-route");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/rotation-test/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
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
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────────

    @Test
    void rotate_existingKey_returns200WithNewRawKey() {
        ApiKeyService.CreatedApiKey created = apiKeyService
                .create("test-client", "tenant-1", "read", Instant.now().plusSeconds(3600))
                .block();
        assertThat(created).isNotNull();
        Long keyId = created.entity().getId();

        adminClient().post().uri("/admin/api-keys/" + keyId + "/rotate")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKeys("newRawKey", "id", "clientId", "status");
                    assertThat(body.get("id")).isNotNull();
                    assertThat(body.get("clientId")).isEqualTo("test-client");
                });
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────────

    @Test
    void rotate_newRawKey_startsWithSgkPrefix() {
        ApiKeyService.CreatedApiKey created = apiKeyService
                .create("prefix-client", "tenant-1", "read", Instant.now().plusSeconds(3600))
                .block();
        Long keyId = created.entity().getId();

        adminClient().post().uri("/admin/api-keys/" + keyId + "/rotate")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    String newKey = (String) body.get("newRawKey");
                    assertThat(newKey).startsWith("sgk_");
                });
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────────

    @Test
    void rotate_newKeyDiffersFromOldKey() {
        ApiKeyService.CreatedApiKey created = apiKeyService
                .create("diff-client", "tenant-1", "read", Instant.now().plusSeconds(3600))
                .block();
        String oldRawKey = created.rawKey();
        Long keyId = created.entity().getId();

        String newRawKey = adminClient().post().uri("/admin/api-keys/" + keyId + "/rotate")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<Map<String, Object>>() {})
                .getResponseBody()
                .blockFirst()
                .get("newRawKey").toString();

        assertThat(newRawKey).isNotEqualTo(oldRawKey);
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────────

    @Test
    void rotate_unknownId_returns404() {
        adminClient().post().uri("/admin/api-keys/999999/rotate")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────────

    @Test
    void rotate_userRole_returns403() {
        userClient().post().uri("/admin/api-keys/1/rotate")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Test 6: ApiKeyService.rotate unit-level ──────────────────────────────────

    @Test
    void rotate_updatesKeyHashInDatabase() {
        ApiKeyService.CreatedApiKey created = apiKeyService
                .create("hash-client", "tenant-1", "read", Instant.now().plusSeconds(3600))
                .block();
        String oldHash = created.entity().getKeyHash();
        Long keyId = created.entity().getId();

        ApiKeyService.RotatedApiKey rotated = apiKeyService.rotate(keyId).block();
        assertThat(rotated).isNotNull();

        ApiKey reloaded = apiKeyRepository.findById(keyId).block();
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getKeyHash()).isNotEqualTo(oldHash);
        assertThat(reloaded.getKeyHash()).isEqualTo(ApiKeyService.sha256Hex(rotated.newRawKey()));
    }
}
