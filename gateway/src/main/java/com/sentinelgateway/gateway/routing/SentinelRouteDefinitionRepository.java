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
            methodPredicate.setArgs(Map.of("_genkey_0", entity.getMethods()));
            predicates.add(methodPredicate);
        }
        def.setPredicates(predicates);

        List<FilterDefinition> filters = new ArrayList<>();

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

        if (resilience.isEnabled() && resilience.getCircuitBreaker().isEnabled()) {
            var cb = new FilterDefinition();
            cb.setName("CircuitBreaker");
            cb.setArgs(Map.of("name", entity.getRouteId() + "-cb"));
            filters.add(cb);
        }

        def.setFilters(filters);
        return def;
    }
}
