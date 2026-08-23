package com.sentinelgateway.gateway.routing;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive R2DBC repository for persistent route definitions.
 *
 * Backed by H2 in dev/test; PostgreSQL in Docker/K8s via R2DBC_URL override.
 */
@Repository
public interface RouteRepository extends ReactiveCrudRepository<RouteEntity, Long> {

    Mono<RouteEntity> findByRouteId(String routeId);

    Flux<RouteEntity> findAllByEnabled(boolean enabled);

    Mono<Boolean> existsByRouteId(String routeId);

    Mono<Void> deleteByRouteId(String routeId);
}
