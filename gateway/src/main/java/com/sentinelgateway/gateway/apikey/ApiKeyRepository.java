package com.sentinelgateway.gateway.apikey;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ApiKeyRepository extends ReactiveCrudRepository<ApiKey, Long> {

    Mono<ApiKey> findByKeyHash(String keyHash);
}
