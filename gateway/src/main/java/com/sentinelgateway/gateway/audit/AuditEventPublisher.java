package com.sentinelgateway.gateway.audit;

import reactor.core.publisher.Mono;

/**
 * Contract for publishing {@link AuditEvent}s asynchronously.
 *
 * Default implementation: {@link LoggingAuditEventPublisher} (structured log).
 * Production implementation: {@link KafkaAuditEventPublisher} (Kafka topic).
 *
 * Implementations must be non-blocking — they run within the reactive request
 * pipeline and must never perform blocking I/O directly.
 */
public interface AuditEventPublisher {

    /**
     * Publish an audit event.
     *
     * @return a {@code Mono<Void>} that completes when the event has been
     *         handed off (not necessarily when it has been durably stored).
     */
    Mono<Void> publish(AuditEvent event);
}
