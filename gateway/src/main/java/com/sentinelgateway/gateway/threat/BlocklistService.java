package com.sentinelgateway.gateway.threat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Manages the IP blocklist at runtime.
 *
 * Persists blocked IPs in Redis (key: {@code sentinel:blocklist}) so entries
 * survive gateway restarts and are shared across clustered instances.
 * Also keeps ThreatDetectionProperties.blockedIps in sync so the synchronous
 * ThreatDetectionFilter can check without a Redis round-trip per request.
 *
 * This service is conditional on a ReactiveRedisOperations bean being present
 * (i.e., Redis is configured). When Redis is absent, use YAML-only blocklist.
 */
@Service
@ConditionalOnBean(ReactiveRedisOperations.class)
public class BlocklistService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BlocklistService.class);
    private static final String REDIS_KEY = "sentinel:blocklist";

    private final ReactiveRedisOperations<String, String> redis;
    private final ThreatDetectionProperties properties;

    public BlocklistService(ReactiveRedisOperations<String, String> redis,
                            ThreatDetectionProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        redis.opsForSet().members(REDIS_KEY)
                .collectList()
                .doOnNext(ips -> {
                    if (!ips.isEmpty()) {
                        properties.getBlockedIps().addAll(ips);
                        log.info("Loaded {} blocked IPs from Redis", ips.size());
                    }
                })
                .subscribe();
    }

    /** Returns all currently blocked IPs (YAML-static + Redis-persisted). */
    public Flux<String> listBlockedIps() {
        return Flux.fromIterable(Set.copyOf(properties.getBlockedIps()));
    }

    /** Add an IP to the blocklist. Persists to Redis and updates in-memory list. */
    public Mono<Void> blockIp(String ip) {
        return redis.opsForSet().add(REDIS_KEY, ip)
                .doOnNext(added -> {
                    if (!properties.getBlockedIps().contains(ip)) {
                        properties.getBlockedIps().add(ip);
                    }
                    log.info("Blocked IP added: {}", ip);
                })
                .then();
    }

    /** Remove an IP from the Redis-managed blocklist. Static YAML entries cannot be removed. */
    public Mono<Boolean> unblockIp(String ip) {
        return redis.opsForSet().remove(REDIS_KEY, ip)
                .map(removed -> {
                    properties.getBlockedIps().remove(ip);
                    if (removed > 0) {
                        log.info("Blocked IP removed: {}", ip);
                    }
                    return removed > 0;
                });
    }
}
