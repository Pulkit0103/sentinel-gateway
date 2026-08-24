package com.sentinelgateway.gateway.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

/**
 * Real implementation of {@link WebhookService} that delivers events to configured
 * HTTP endpoints with HMAC-SHA256 signing and retry support.
 *
 * <p>Delivery is fire-and-forget: {@link #emit} always returns {@code Mono.empty()}
 * to the caller; actual HTTP posts are subscribed on {@link Schedulers#boundedElastic()}
 * so they never block the gateway request pipeline.
 */
public class WebhookServiceImpl implements WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);

    private final WebClient webClient;
    private final WebhookProperties properties;
    private final ObjectMapper objectMapper;

    public WebhookServiceImpl(WebClient webClient,
                               WebhookProperties properties,
                               ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> emit(WebhookEvent event) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }

        List<WebhookProperties.WebhookEndpoint> endpoints = properties.getEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            return Mono.empty();
        }

        // Build and subscribe asynchronously for each matching endpoint
        Flux.fromIterable(endpoints)
                .filter(ep -> acceptsEvent(ep, event.eventType()))
                .flatMap(ep -> deliverToEndpoint(ep, event))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        err -> log.warn("Webhook delivery error for event {}: {}",
                                event.eventType(), err.getMessage())
                );

        // Always return empty — fire-and-forget
        return Mono.empty();
    }

    /**
     * Returns {@code true} if the endpoint accepts the given event type.
     * An empty events list means "accept all".
     */
    private boolean acceptsEvent(WebhookProperties.WebhookEndpoint endpoint, String eventType) {
        List<String> filter = endpoint.getEvents();
        return filter == null || filter.isEmpty() || filter.contains(eventType);
    }

    /**
     * Serializes and POSTs the event to a single endpoint with retry.
     */
    private Mono<Void> deliverToEndpoint(WebhookProperties.WebhookEndpoint endpoint,
                                          WebhookEvent event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .flatMap(json -> {
                    String signature = sign(json, endpoint.getSecret());
                    return webClient.post()
                            .uri(endpoint.getUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Webhook-Signature", signature)
                            .bodyValue(json)
                            .retrieve()
                            .bodyToMono(Void.class)
                            .retry(properties.getRetryAttempts());
                })
                .doOnError(err -> log.warn("Failed to deliver webhook {} to {}: {}",
                        event.eventType(), endpoint.getUrl(), err.getMessage()))
                .onErrorResume(err -> Mono.empty());
    }

    /**
     * Computes an HMAC-SHA256 signature of the given body using the provided secret.
     *
     * @param body   the JSON string to sign
     * @param secret the signing secret (UTF-8 encoded)
     * @return {@code "sha256={hexDigest}"}
     */
    String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("HMAC signing failed", e);
            return "sha256=error";
        }
    }
}
