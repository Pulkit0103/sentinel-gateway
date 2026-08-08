package com.sentinelgateway.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Top-level Sentinel Gateway application properties.
 *
 * Route-specific configuration has moved to
 * {@link com.sentinelgateway.gateway.routing.RouteDefinitionProperties}.
 *
 * This class retains gateway-level settings that don't belong to any
 * specific subsystem (e.g., global timeouts, admin settings in later phases).
 */
@Component
@ConfigurationProperties(prefix = "sentinel.gateway")
public class SentinelGatewayProperties {

    // Placeholder for future gateway-level settings.
    // Route definitions are in RouteDefinitionProperties (sentinel.gateway.routes[*]).
}
