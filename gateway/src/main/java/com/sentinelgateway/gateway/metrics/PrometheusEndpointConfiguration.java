package com.sentinelgateway.gateway.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusSimpleclientScrapeEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Spring Boot 3.3.4 workaround: PrometheusSimpleclientMetricsExportAutoConfiguration skips itself
// when it detects that a PrometheusSimpleclientScrapeEndpoint bean definition already exists
// (via @ConditionalOnMissingBean), so it never registers CollectorRegistry or PrometheusMeterRegistry.
// This class is self-contained and provides all three beans so the gap is filled regardless of
// autoconfiguration ordering.
@Configuration
@ConditionalOnClass(name = "io.micrometer.prometheus.PrometheusMeterRegistry")
public class PrometheusEndpointConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CollectorRegistry collectorRegistry() {
        // Use a fresh registry per context rather than the JVM-wide defaultRegistry.
        // Sharing defaultRegistry across test contexts causes duplicate-collector conflicts
        // when Spring Cloud Gateway tries to re-register spring_cloud_gateway_routes_count.
        return new CollectorRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(PrometheusMeterRegistry.class)
    public PrometheusMeterRegistry prometheusMeterRegistry(CollectorRegistry collectorRegistry) {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, collectorRegistry, Clock.SYSTEM);
    }

    @Bean
    @ConditionalOnAvailableEndpoint(endpoint = PrometheusSimpleclientScrapeEndpoint.class)
    @ConditionalOnMissingBean({PrometheusSimpleclientScrapeEndpoint.class, PrometheusScrapeEndpoint.class})
    public PrometheusSimpleclientScrapeEndpoint prometheusEndpoint(CollectorRegistry collectorRegistry) {
        return new PrometheusSimpleclientScrapeEndpoint(collectorRegistry);
    }
}
