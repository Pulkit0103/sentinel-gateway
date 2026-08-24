package com.sentinelgateway.gateway.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Spring configuration for the webhook event emission subsystem.
 *
 * <p>When {@code sentinel.webhook.enabled=true}:
 * <ul>
 *   <li>{@link WebhookServiceImpl} is registered — makes real HTTP calls.</li>
 * </ul>
 * Otherwise:
 * <ul>
 *   <li>{@link NoOpWebhookService} is registered — all emits are no-ops.</li>
 * </ul>
 */
@Configuration
public class WebhookConfig {

    /**
     * Dedicated {@link WebClient} for webhook delivery, configured with a per-request
     * response timeout derived from {@code sentinel.webhook.timeout-seconds}.
     *
     * <p>Timeout is applied at the Reactor Netty {@link HttpClient} level, which is the
     * correct layer for WebFlux-based WebClient timeout configuration.
     */
    @Bean
    public WebClient webhookWebClient(WebhookProperties props) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * Real webhook service — active only when {@code sentinel.webhook.enabled=true}.
     */
    @Bean
    @ConditionalOnProperty(name = "sentinel.webhook.enabled", havingValue = "true")
    public WebhookService webhookService(WebClient webhookWebClient,
                                         WebhookProperties properties,
                                         ObjectMapper objectMapper) {
        return new WebhookServiceImpl(webhookWebClient, properties, objectMapper);
    }

    /**
     * No-op fallback — registered when no real {@link WebhookService} bean is present
     * (i.e., when webhooks are disabled).
     */
    @Bean
    @ConditionalOnMissingBean(WebhookService.class)
    public WebhookService noOpWebhookService() {
        return new NoOpWebhookService();
    }
}
