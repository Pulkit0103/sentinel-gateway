package com.sentinelgateway.gateway.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration smoke test verifying the application context loads correctly with
 * the webhook subsystem present.
 *
 * <p>Uses {@code @MockBean WebhookService} to avoid needing a real HTTP endpoint
 * while still confirming wiring is valid.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebhookIntegrationTest {

    @MockBean
    WebhookService webhookService;

    @Autowired(required = false)
    WebhookProperties webhookProperties;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Provide a dummy JWKS URI to satisfy Spring Security resource server config
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:9999/jwks-unused");
        registry.add("sentinel.security.jwt.issuer", () -> "");
    }

    @Test
    void contextLoads() {
        // The context must start without errors — this verifies all webhook wiring is correct
        assertThat(webhookProperties).isNotNull();
        assertThat(webhookProperties.isEnabled()).isFalse();
    }
}
