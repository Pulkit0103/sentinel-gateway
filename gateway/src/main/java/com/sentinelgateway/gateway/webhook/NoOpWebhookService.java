package com.sentinelgateway.gateway.webhook;

import reactor.core.publisher.Mono;

/**
 * No-op implementation of {@link WebhookService} used when
 * {@code sentinel.webhook.enabled} is {@code false} (the default).
 *
 * <p>Every call to {@link #emit} returns {@code Mono.empty()} immediately
 * without making any HTTP requests or serializing any data.
 */
public class NoOpWebhookService implements WebhookService {

    @Override
    public Mono<Void> emit(WebhookEvent event) {
        return Mono.empty();
    }
}
