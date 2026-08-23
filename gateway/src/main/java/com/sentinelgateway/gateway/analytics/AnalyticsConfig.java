package com.sentinelgateway.gateway.analytics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Registers either the Redis-backed or No-Op {@link AnalyticsService} bean
 * depending on the {@code sentinel.analytics.enabled} property.
 */
@Configuration
public class AnalyticsConfig {

    @Bean
    @ConditionalOnProperty(name = "sentinel.analytics.enabled", havingValue = "true")
    public AnalyticsService redisAnalyticsService(ReactiveStringRedisTemplate redisTemplate,
                                                   AnalyticsProperties props) {
        return new RedisAnalyticsService(redisTemplate, props);
    }

    @Bean
    @ConditionalOnMissingBean(AnalyticsService.class)
    public AnalyticsService noOpAnalyticsService() {
        return new NoOpAnalyticsService();
    }
}
