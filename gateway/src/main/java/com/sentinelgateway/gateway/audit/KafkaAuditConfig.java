package com.sentinelgateway.gateway.audit;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Manually configures Kafka producer beans when audit Kafka publishing is enabled.
 *
 * Spring Boot's KafkaAutoConfiguration is excluded globally (application.yml) to
 * prevent connection failures in environments where Kafka is not running.
 * This class creates only the beans needed for audit event publishing, with no
 * side effects when Kafka is unavailable ({@code sentinel.audit.kafka.enabled=false}).
 */
@Configuration
@ConditionalOnProperty(name = "sentinel.audit.kafka.enabled", havingValue = "true")
class KafkaAuditConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    ProducerFactory<String, AuditEvent> auditProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        props.put(ProducerConfig.ACKS_CONFIG, "1");          // leader ack — balances durability/latency
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    KafkaTemplate<String, AuditEvent> auditKafkaTemplate(
            ProducerFactory<String, AuditEvent> auditProducerFactory) {
        return new KafkaTemplate<>(auditProducerFactory);
    }
}
