package com.sentinelgateway.gateway.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

/**
 * Manages the route lifecycle: seeding, CRUD, and hot-reload.
 *
 * On startup, if the routes table is empty, seeds it from YAML configuration
 * (RouteDefinitionProperties). After that, the database is authoritative.
 *
 * Every mutating operation also updates RouteRegistry (in-memory) and publishes
 * a RefreshRoutesEvent so Spring Cloud Gateway immediately picks up the change.
 */
@Service
public class RouteService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);

    private final RouteRepository repository;
    private final RouteDefinitionProperties properties;
    private final RouteRegistry registry;
    private final ApplicationEventPublisher events;

    public RouteService(RouteRepository repository,
                        RouteDefinitionProperties properties,
                        RouteRegistry registry,
                        ApplicationEventPublisher events) {
        this.repository = repository;
        this.properties = properties;
        this.registry = registry;
        this.events = events;
    }

    @Override
    public void run(ApplicationArguments args) {
        repository.count()
                .flatMap(count -> {
                    if (count == 0) {
                        List<RouteDefinition> yamlRoutes = properties.toRouteDefinitions();
                        if (yamlRoutes.isEmpty()) {
                            log.info("No routes in DB or YAML — starting with empty route table");
                            return Mono.empty();
                        }
                        log.info("Seeding {} routes from YAML into database", yamlRoutes.size());
                        List<RouteEntity> entities = yamlRoutes.stream()
                                .map(RouteEntity::from)
                                .toList();
                        return repository.saveAll(entities).then();
                    }
                    log.info("Routes table has {} rows — skipping YAML seed", count);
                    return Mono.empty();
                })
                .then(repository.findAll().map(RouteEntity::toDomain).collectList())
                .doOnNext(routes -> {
                    registry.loadAll(routes);
                    log.info("RouteRegistry loaded with {} routes", routes.size());
                })
                .blockOptional(Duration.ofSeconds(15));
        // Tell Spring Cloud Gateway to re-read routes from the now-populated DB.
        // Without this, CachingRouteLocator keeps the empty snapshot it took
        // at context-refresh time (before ApplicationRunner ran).
        refresh();
    }

    public Flux<RouteDefinition> findAll() {
        return repository.findAll().map(RouteEntity::toDomain);
    }

    public Mono<RouteDefinition> findById(String routeId) {
        return repository.findByRouteId(routeId)
                .map(RouteEntity::toDomain)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Route not found: " + routeId)));
    }

    public Mono<RouteDefinition> create(RouteDefinition definition) {
        return repository.existsByRouteId(definition.routeId())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT, "Route already exists: " + definition.routeId()));
                    }
                    return repository.save(RouteEntity.from(definition));
                })
                .map(RouteEntity::toDomain)
                .doOnNext(route -> {
                    registry.register(route);
                    refresh();
                    log.info("Route created: {}", route.routeId());
                });
    }

    public Mono<RouteDefinition> update(String routeId, RouteDefinition updated) {
        return repository.findByRouteId(routeId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Route not found: " + routeId)))
                .flatMap(entity -> {
                    entity.setPath(updated.path());
                    entity.setServiceUri(updated.serviceUri());
                    entity.setMethods(updated.methods().isEmpty() ? null : String.join(",", updated.methods()));
                    entity.setEnabled(updated.enabled());
                    entity.setRequiredScopes(updated.requiredScopes().isEmpty() ? null : String.join(",", updated.requiredScopes()));
                    entity.setTenantRequired(updated.tenantRequired());
                    entity.setRateLimitPolicy(updated.rateLimitPolicy());
                    entity.setStripPrefix(updated.stripPrefix());
                    entity.setAddRequestHeaders(RouteEntity.from(updated).getAddRequestHeaders());
                    entity.setAllowedIps(RouteEntity.from(updated).getAllowedIps());
                    entity.setBlockedIps(RouteEntity.from(updated).getBlockedIps());
                    entity.setMaxBodyBytes(updated.maxBodyBytes());
                    entity.setTimeoutMs(updated.timeoutMs());
                    entity.setCacheTtlSeconds(updated.cacheTtlSeconds());
                    entity.setUpdatedAt(LocalDateTime.now());
                    return repository.save(entity);
                })
                .map(RouteEntity::toDomain)
                .doOnNext(route -> {
                    registry.register(route);
                    refresh();
                    log.info("Route updated: {}", route.routeId());
                });
    }

    public Mono<Void> delete(String routeId) {
        return repository.existsByRouteId(routeId)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Route not found: " + routeId));
                    }
                    return repository.deleteByRouteId(routeId);
                })
                .doOnSuccess(v -> {
                    registry.deregister(routeId);
                    refresh();
                    log.info("Route deleted: {}", routeId);
                });
    }

    public Mono<RouteDefinition> setEnabled(String routeId, boolean enabled) {
        return repository.findByRouteId(routeId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Route not found: " + routeId)))
                .flatMap(entity -> {
                    entity.setEnabled(enabled);
                    entity.setUpdatedAt(LocalDateTime.now());
                    return repository.save(entity);
                })
                .map(RouteEntity::toDomain)
                .doOnNext(route -> {
                    registry.register(route);
                    refresh();
                    log.info("Route '{}' {}", routeId, enabled ? "enabled" : "disabled");
                });
    }

    private void refresh() {
        events.publishEvent(new RefreshRoutesEvent(this));
    }
}
