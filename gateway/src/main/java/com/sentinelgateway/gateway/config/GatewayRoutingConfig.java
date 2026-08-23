package com.sentinelgateway.gateway.config;

import org.springframework.context.annotation.Configuration;

/**
 * Gateway routing configuration.
 *
 * Phase 1: routes were built statically from YAML via RouteLocatorBuilder.
 * Phase 2: routes are managed dynamically via SentinelRouteDefinitionRepository
 *          (database-backed) and the Admin CRUD API. Spring Cloud Gateway
 *          reads routes from that repository; this class is now a placeholder.
 */
@Configuration
public class GatewayRoutingConfig {
    // Route definitions are provided by SentinelRouteDefinitionRepository.
    // Add @Bean methods here only for cross-cutting gateway filters or
    // global configuration that cannot be expressed as route predicates.
}
