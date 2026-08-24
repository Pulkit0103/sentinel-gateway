package com.sentinelgateway.gateway.transform;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for ResponseHeadersFilter.
 *
 * Verifies that implementation-leaking headers returned by upstream are removed
 * from the response sent to clients.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ResponseHeadersFilterTest {

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

        wireMock.stubFor(any(urlPathMatching("/api/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Server", "evil-server/1.0")
                        .withHeader("X-Powered-By", "ExposeMe/9.9")
                        .withHeader("Via", "proxy.internal.corp")
                        .withBody("{\"ok\":true}")));

        String base = "http://localhost:" + wireMock.port();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> base + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "");

        registry.add("sentinel.gateway.routes[0].route-id",    () -> "response-test-service");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/response/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> base);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null && wireMock.isRunning()) wireMock.stop();
    }

    private WebTestClient authed() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    @Test
    void serverHeader_isRemovedFromResponse() {
        authed().get().uri("/api/response/resource")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("Server");
    }

    @Test
    void xPoweredByHeader_isRemovedFromResponse() {
        authed().get().uri("/api/response/resource")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("X-Powered-By");
    }

    @Test
    void viaHeader_isRemovedFromResponse() {
        authed().get().uri("/api/response/resource")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("Via");
    }
}
