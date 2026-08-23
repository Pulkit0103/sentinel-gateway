package com.sentinelgateway.gateway.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Fallback audit publisher that writes events to the structured application log.
 *
 * Active when Kafka is not configured ({@code sentinel.audit.kafka.enabled=false}).
 * Audit events appear as INFO log lines with the {@code AUDIT} marker — easily
 * filterable by log aggregation tools (Loki, Splunk, CloudWatch).
 *
 * Registered conditionally via {@link AuditConfig} — not a Spring component itself.
 */
public class LoggingAuditEventPublisher implements AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    @Override
    public Mono<Void> publish(AuditEvent event) {
        return Mono.fromRunnable(() ->
                log.info("requestId={} timestamp={} ip={} method={} path={} route={} status={} outcome={}",
                        event.requestId(), event.timestamp(), event.clientIp(),
                        event.method(), event.path(), event.routeId(),
                        event.responseStatus(), event.outcome())
        );
    }
}
