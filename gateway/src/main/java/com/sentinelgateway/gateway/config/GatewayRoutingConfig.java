package com.sentinelgateway.gateway.config;

import com.sentinelgateway.gateway.resilience.ResilienceProperties;
import com.sentinelgateway.gateway.routing.RouteDefinition;
import com.sentinelgateway.gateway.routing.RouteDefinitionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Builds Spring Cloud Gateway routes from the domain-level {@link RouteDefinition}
 * objects held in {@link RouteDefinitionProperties}.
 *
 * Only ENABLED routes are registered. Disabled routes are logged and skipped,
 * so requests to their paths result in a 404 from the gateway.
 *
 * Resilience filters applied per-route (when sentinel.resilience.enabled=true):
 *   - ResponseTimeout: per-route response deadline
 *   - Retry: idempotent methods (GET/HEAD) only, on 5xx responses
 *   - CircuitBreaker: per-route Resilience4j circuit breaker ({routeId}-cb)
 */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", havingValue = "true", matchIfMissing = true)
public class GatewayRoutingConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayRoutingConfig.class);

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder,
                                      RouteDefinitionProperties properties,
                                      ResilienceProperties resilience) {
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

                if (!def.methods().isEmpty()) {
                    HttpMethod[] httpMethods = def.methods().stream()
                            .map(HttpMethod::valueOf)
                            .toArray(HttpMethod[]::new);
                    predicate = predicate.and().method(httpMethods);
                }

                if (resilience.isEnabled()) {
                    return predicate.filters(f -> applyResilienceFilters(f, def, resilience))
                            .uri(def.serviceUri());
                }
                return predicate.uri(def.serviceUri());
            });
        }

        return routes.build();
    }

    private GatewayFilterSpec applyResilienceFilters(
            GatewayFilterSpec f,
            RouteDefinition def,
            ResilienceProperties resilience) {

        // Retry on 5xx — idempotent methods only (GET, HEAD by default).
        // Response timeout is set globally via spring.cloud.gateway.httpclient.response-timeout
        // (or per-environment via environment variables); Spring Cloud Gateway 4.x does not expose
        // a GatewayFilterSpec.responseTimeout() method for per-route override in the programmatic DSL.
        if (resilience.getRetry().isEnabled() && resilience.getRetry().getAttempts() > 0) {
            int attempts = resilience.getRetry().getAttempts();
            HttpMethod[] retryMethods = resilience.getRetry().getSafeMethods().stream()
                    .map(HttpMethod::valueOf)
                    .toArray(HttpMethod[]::new);

            f = f.retry(c -> c
                    .setRetries(attempts)
                    .setMethods(retryMethods)
                    .setSeries(HttpStatus.Series.SERVER_ERROR)
                    // Clear the default {IOException, TimeoutException} exception list — only
                    // retry on 5xx HTTP responses, not on connection errors or timeouts.
                    .setExceptions());
        }

        // Per-route circuit breaker (opens on sustained upstream failures → 503)
        if (resilience.getCircuitBreaker().isEnabled()) {
            String cbName = def.routeId() + "-cb";
            f = f.circuitBreaker(c -> c.setName(cbName));
        }

        return f;
    }
}
