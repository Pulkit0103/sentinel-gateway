package com.sentinelgateway.gateway.policy;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for security policies.
 *
 * Backed by H2 in dev/test; swap to PostgreSQL R2DBC in prod/Docker
 * by overriding the R2DBC_URL environment variable.
 */
@Repository
public interface SecurityPolicyRepository extends ReactiveCrudRepository<SecurityPolicy, Long> {

    Mono<SecurityPolicy> findByRouteId(String routeId);

    Mono<SecurityPolicy> findByPolicyId(String policyId);
}
