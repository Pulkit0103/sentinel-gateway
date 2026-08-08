package com.sentinelgateway.gateway.config;

import com.sentinelgateway.gateway.routing.RouteDefinition;
import com.sentinelgateway.gateway.routing.RouteDefinitionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.util.List;

/**
 * Builds Spring Cloud Gateway routes from the domain-level {@link RouteDefinition}
 * objects held in {@link RouteDefinitionProperties}.
 *
 * Only ENABLED routes are registered. Disabled routes are logged and skipped,
 * so requests to their paths result in a 404 from the gateway.
 *
 * Method restrictions are enforced at the route-predicate level: a request to
 * a valid path with an unsupported HTTP method will not match any route and
 * receives a 405 Method Not Allowed (Spring Cloud Gateway's default behaviour
 * when the path matches but the method predicate fails).
 *
 * Security filters (auth, authz, rate limiting, etc.) are added in later phases.
 */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", havingValue = "true", matchIfMissing = true)
public class GatewayRoutingConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayRoutingConfig.class);

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder,
                                      RouteDefinitionProperties properties) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        List<RouteDefinition> definitions = properties.toRouteDefinitions();

        for (RouteDefinition def : definitions) {
            if (!def.enabled()) {
                log.info("Route '{}' is disabled — skipping registration", def.routeId());
                continue;
            }

            log.info("Registering route '{}': {} → {}", def.routeId(), def.path(), def.serviceUri());

            routes.route(def.routeId(), r -> {
                var predicate = r.path(def.path());

                // Restrict to declared HTTP methods if any are configured.
                if (!def.methods().isEmpty()) {
                    HttpMethod[] httpMethods = def.methods().stream()
                            .map(HttpMethod::valueOf)
                            .toArray(HttpMethod[]::new);
                    predicate = predicate.and().method(httpMethods);
                }

                return predicate.uri(def.serviceUri());
            });
        }

        return routes.build();
    }
}
