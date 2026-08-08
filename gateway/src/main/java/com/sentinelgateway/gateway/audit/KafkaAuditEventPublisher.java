package com.sentinelgateway.gateway.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Publishes audit events to a Kafka topic asynchronously.
 *
 * Uses the gateway-scoped {@link KafkaTemplate} configured by {@link KafkaAuditConfig}.
 * The producer is fire-and-forget at the call site; delivery failures are surfaced
 * via the callback to the application log (not propagated to the caller).
 *
 * Active only when {@code sentinel.audit.kafka.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "sentinel.audit.kafka.enabled", havingValue = "true")
public class KafkaAuditEventPublisher implements AuditEventPublisher {

    @Value("${sentinel.audit.kafka.topic:sentinel.audit.requests}")
    private String topic;

    private final KafkaTemplate<String, AuditEvent> kafkaTemplate;

    public KafkaAuditEventPublisher(KafkaTemplate<String, AuditEvent> auditKafkaTemplate) {
        this.kafkaTemplate = auditKafkaTemplate;
    }

    @Override
    public Mono<Void> publish(AuditEvent event) {
        // send() returns CompletableFuture in Spring Kafka 3.x
        return Mono.fromFuture(
                kafkaTemplate.send(topic, event.requestId(), event).toCompletableFuture()
        ).then();
    }
}
