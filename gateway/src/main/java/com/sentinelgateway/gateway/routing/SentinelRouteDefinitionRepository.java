package com.sentinelgateway.gateway.routing;

import com.sentinelgateway.gateway.resilience.ResilienceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Cloud Gateway RouteDefinitionRepository backed by the database.
 *
 * Reads from RouteRepository (R2DBC) and translates RouteEntity objects into
 * the Spring Cloud Gateway route definition format, applying resilience filters
 * (CircuitBreaker, Retry) when enabled.
 *
 * Publishing a RefreshRoutesEvent causes Spring Cloud Gateway to call
 * getRouteDefinitions() again, picking up any route changes made via Admin API.
 */
@Component
public class SentinelRouteDefinitionRepository implements RouteDefinitionRepository {

    private static final Logger log = LoggerFactory.getLogger(SentinelRouteDefinitionRepository.class);

    private final RouteRepository routeRepository;
    private final ResilienceProperties resilience;

    public SentinelRouteDefinitionRepository(RouteRepository routeRepository,
                                              ResilienceProperties resilience) {
        this.routeRepository = routeRepository;
        this.resilience = resilience;
    }

    @Override
    public Flux<org.springframework.cloud.gateway.route.RouteDefinition> getRouteDefinitions() {
        return routeRepository.findAllByEnabled(true)
                .map(this::toGatewayDefinition)
                .doOnNext(d -> log.debug("Serving route: {}", d.getId()));
    }

    @Override
    public Mono<Void> save(Mono<org.springframework.cloud.gateway.route.RouteDefinition> route) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return Mono.empty();
    }

    private org.springframework.cloud.gateway.route.RouteDefinition toGatewayDefinition(RouteEntity entity) {
        var def = new org.springframework.cloud.gateway.route.RouteDefinition();
        def.setId(entity.getRouteId());
        def.setUri(URI.create(entity.getServiceUri()));

        List<PredicateDefinition> predicates = new ArrayList<>();

        var pathPredicate = new PredicateDefinition();
        pathPredicate.setName("Path");
        pathPredicate.setArgs(Map.of("_genkey_0", entity.getPath()));
        predicates.add(pathPredicate);

        if (entity.getMethods() != null && !entity.getMethods().isBlank()) {
            var methodPredicate = new PredicateDefinition();
            methodPredicate.setName("Method");
            // Method predicate uses GATHER_LIST shortcut: each method must be its own _genkey_N arg.
            // A single "_genkey_0=GET,POST" is NOT split by Spring Cloud Gateway — each method
            // needs a separate numbered key for the predicate to match all listed methods.
            String[] methods = entity.getMethods().split(",");
            Map<String, String> methodArgs = new LinkedHashMap<>();
            for (int i = 0; i < methods.length; i++) {
                methodArgs.put("_genkey_" + i, methods[i].trim());
            }
            methodPredicate.setArgs(methodArgs);
            predicates.add(methodPredicate);
        }
        def.setPredicates(predicates);

        List<FilterDefinition> filters = new ArrayList<>();

        if (entity.getStripPrefix() > 0) {
            var stripPrefix = new FilterDefinition();
            stripPrefix.setName("StripPrefix");
            stripPrefix.setArgs(Map.of("parts", String.valueOf(entity.getStripPrefix())));
            filters.add(stripPrefix);
        }

        entity.addRequestHeaderMap().forEach((name, value) -> {
            var addHeader = new FilterDefinition();
            addHeader.setName("AddRequestHeader");
            Map<String, String> headerArgs = new LinkedHashMap<>();
            headerArgs.put("name", name);
            headerArgs.put("value", value);
            addHeader.setArgs(headerArgs);
            filters.add(addHeader);
        });

        if (resilience.isEnabled() && resilience.getRetry().isEnabled()
                && resilience.getRetry().getAttempts() > 0) {
            var retry = new FilterDefinition();
            retry.setName("Retry");
            Map<String, String> retryArgs = new LinkedHashMap<>();
            retryArgs.put("retries", String.valueOf(resilience.getRetry().getAttempts()));
            retryArgs.put("series", "SERVER_ERROR");
            retryArgs.put("methods", String.join(",", resilience.getRetry().getSafeMethods()));
            retry.setArgs(retryArgs);
            filters.add(retry);
        }

        if (entity.getMaxBodyBytes() != null && entity.getMaxBodyBytes() > 0) {
            var requestSize = new FilterDefinition();
            requestSize.setName("RequestSize");
            requestSize.setArgs(Map.of("maxSize", entity.getMaxBodyBytes() + "B"));
            filters.add(requestSize);
        }

        if (resilience.isEnabled() && resilience.getCircuitBreaker().isEnabled()) {
            var cb = new FilterDefinition();
            cb.setName("CircuitBreaker");
            cb.setArgs(Map.of("name", entity.getRouteId() + "-cb"));
            filters.add(cb);
        }

        def.setFilters(filters);

        // Per-route timeout via route metadata — read by NettyRoutingFilter.
        // NettyRoutingFilter.getLong() expects a Number (Long millis) for "response-timeout";
        // it calls Duration.ofMillis() internally. Passing a Duration directly would cause
        // toString() → parseLong() to throw NumberFormatException and silently fall back to
        // the global timeout. "connect-timeout" expects an Integer (millis).
        if (entity.getTimeoutMs() != null && entity.getTimeoutMs() > 0) {
            def.getMetadata().put("response-timeout", entity.getTimeoutMs());
            def.getMetadata().put("connect-timeout", (int) Math.min(entity.getTimeoutMs(), 3000L));
        }

        return def;
    }
}
