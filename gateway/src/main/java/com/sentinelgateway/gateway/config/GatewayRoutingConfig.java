package com.sentinelgateway.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines gateway routes for Phase 1.
 *
 * Routes in Phase 1 are intentionally minimal — no authentication or
 * authorization filters are applied yet. Security controls are introduced
 * incrementally starting from Phase 3.
 *
 * The HELLO_SERVICE_URL environment variable allows the hello-service
 * location to be overridden for Docker / Kubernetes without code changes.
 */
@Configuration
public class GatewayRoutingConfig {

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder, SentinelGatewayProperties properties) {
        return builder.routes()
                .route("hello-service", r -> r
                        .path("/api/hello/**")
                        .uri(properties.getHelloServiceUrl()))
                .build();
    }
}
