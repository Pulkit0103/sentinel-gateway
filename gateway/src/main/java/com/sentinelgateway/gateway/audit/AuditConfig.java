package com.sentinelgateway.gateway.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the fallback {@link LoggingAuditEventPublisher} when no other
 * {@link AuditEventPublisher} bean is present (e.g. Kafka publisher is disabled).
 *
 * {@code @ConditionalOnMissingBean} is only reliable inside a {@code @Configuration}
 * class because Spring processes configuration classes before component scanning
 * resolves constructor-injected dependencies.
 */
@Configuration
public class AuditConfig {

    @Bean
    @ConditionalOnMissingBean(AuditEventPublisher.class)
    public AuditEventPublisher loggingAuditEventPublisher() {
        return new LoggingAuditEventPublisher();
    }
}
