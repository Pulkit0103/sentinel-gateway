package com.sentinelgateway.gateway.canary;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
 * Canary routing test with weight=0: no request should reach the canary backend.
 *
 * <p>A separate Spring context is required because {@link DynamicPropertySource}
 * values are fixed at context creation time; switching weight at runtime is not
 * possible within the same {@code @SpringBootTest} class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CanaryRoutingZeroWeightTest {

    private static WireMockServer stableWireMock;
    private static WireMockServer canaryWireMock;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        stableWireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        canaryWireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

        stableWireMock.start();
        canaryWireMock.start();

        stableWireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));

        String stableBase = "http://localhost:" + stableWireMock.port();
        String canaryBase  = "http://localhost:" + canaryWireMock.port();

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> stableBase + "/jwks");
        registry.add("sentinel.security.jwt.issuer", () -> "");

        registry.add("sentinel.gateway.routes[0].route-id",    () -> "canary-test-service");
        registry.add("sentinel.gateway.routes[0].path",        () -> "/api/canary/**");
        registry.add("sentinel.gateway.routes[0].service-uri", () -> stableBase);
        registry.add("sentinel.gateway.routes[0].methods",     () -> "GET");
        registry.add("sentinel.gateway.routes[0].enabled",     () -> "true");

        // weight=0 → never route to canary
        registry.add("sentinel.canary.routes.canary-test-service.canary-uri", () -> canaryBase);
        registry.add("sentinel.canary.routes.canary-test-service.weight",     () -> "0");
    }

    @AfterAll
    static void tearDown() {
        if (stableWireMock != null && stableWireMock.isRunning()) stableWireMock.stop();
        if (canaryWireMock != null && canaryWireMock.isRunning()) canaryWireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        stableWireMock.resetAll();
        canaryWireMock.resetAll();

        stableWireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));

        stableWireMock.stubFor(get(urlPathMatching("/api/canary/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"backend\":\"stable\"}")));

        canaryWireMock.stubFor(get(urlPathMatching("/api/canary/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"backend\":\"canary\"}")));
    }

    private WebTestClient authed() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt());
    }

    /**
     * With weight=0 no request must reach the canary backend; all go to stable.
     */
    @Test
    void weight0_noRequestsRouteToCanary_allGoToStable() {
        int requests = 10;
        for (int i = 0; i < requests; i++) {
            authed().get().uri("/api/canary/resource")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.backend").isEqualTo("stable");
        }

        // Stable backend receives all requests
        stableWireMock.verify(moreThanOrExactly(requests),
                getRequestedFor(urlPathMatching("/api/canary/.*")));

        // Canary backend receives none
        canaryWireMock.verify(0, getRequestedFor(urlPathMatching("/api/canary/.*")));
    }
}
