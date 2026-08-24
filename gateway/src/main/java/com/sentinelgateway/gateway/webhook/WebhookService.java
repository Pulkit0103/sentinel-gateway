package com.sentinelgateway.gateway.webhook;

import reactor.core.publisher.Mono;

/**
 * Contract for delivering {@link WebhookEvent}s to configured external endpoints.
 *
 * <p>Implementations must be fire-and-forget: callers invoke {@code emit()} and do
 * not await the result. The Mono returned by {@code emit()} always completes empty
 * from the caller's perspective — delivery is handled on a separate scheduler.
 */
public interface WebhookService {

    /**
     * Emit a gateway event to all matching webhook endpoints.
     *
     * <p>Implementations should:
     * <ol>
     *   <li>Filter endpoints by their {@code events} list (empty list = accept all).</li>
     *   <li>Serialize the event to JSON.</li>
     *   <li>Sign the payload with HMAC-SHA256 using the endpoint secret.</li>
     *   <li>POST to the endpoint URL asynchronously on a bounded-elastic scheduler.</li>
     *   <li>Retry up to the configured number of attempts on transient failures.</li>
     * </ol>
     *
     * @param event the event to deliver
     * @return a {@code Mono<Void>} that completes empty (fire-and-forget)
     */
    Mono<Void> emit(WebhookEvent event);
}
