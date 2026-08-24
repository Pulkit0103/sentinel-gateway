package com.sentinelgateway.gateway.lifecycle;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LifecycleConfig {

    @Bean
    public ShutdownHandler shutdownHandler(ConfigurableApplicationContext applicationContext) {
        return applicationContext::close;
    }
}
