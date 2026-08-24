package com.sentinelgateway.gateway.cache;

import com.sentinelgateway.gateway.routing.RouteRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Phase 11 — Per-Route Response Caching.
 *
 * <p>Caches GET responses in Redis per route. Cache keys use the format:
 * {@code sentinel:cache:{routeId}:{path}[?{query}]}.
 * TTL is read from the route's {@code cache_ttl_seconds} column (NULL = no caching).
 *
 * <p>This bean is only registered when {@code sentinel.cache.enabled=true}.
 * In development and tests the property is {@code false}, so no Redis connection
 * is attempted and the filter is entirely absent from the chain.
 *
 * <p>On a cache <em>miss</em> the response body is captured via a
 * {@link ServerHttpResponseDecorator}, stored in Redis with the configured TTL,
 * and returned to the caller with {@code X-Cache: MISS}.
 * On a cache <em>hit</em> the cached body is written directly to the response
 * with {@code X-Cache: HIT} without touching the upstream service.
 */
@Component
@ConditionalOnProperty(name = "sentinel.cache.enabled", havingValue = "true")
public class ResponseCacheFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ResponseCacheFilter.class);

    /** Key prefix for all cached response entries in Redis. */
    static final String KEY_PREFIX = "sentinel:cache:";

    /** Maximum response body size that will be cached (1 MB). */
    private static final int MAX_CACHEABLE_BYTES = 1024 * 1024;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RouteRepository routeRepository;

    public ResponseCacheFilter(ReactiveStringRedisTemplate redisTemplate,
                               RouteRepository routeRepository) {
        this.redisTemplate = redisTemplate;
        this.routeRepository = routeRepository;
    }

    /**
     * Runs just after {@code RequestSanitizationFilter} (+2) and the
     * {@code TracingFilter} (+3), before JWT revocation (+5).
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 4;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Only cache GET requests
        if (exchange.getRequest().getMethod() != HttpMethod.GET) {
            return chain.filter(exchange);
        }

        // Look up the matched route
        org.springframework.cloud.gateway.route.Route gatewayRoute =
                exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (gatewayRoute == null) {
            return chain.filter(exchange);
        }

        String routeId = gatewayRoute.getId();

        return routeRepository.findByRouteId(routeId)
                .flatMap(entity -> {
                    Integer ttl = entity.getCacheTtlSeconds();
                    if (ttl == null || ttl <= 0) {
                        // Route has no caching configured — pass through
                        return chain.filter(exchange);
                    }
                    String cacheKey = buildCacheKey(
                            routeId,
                            exchange.getRequest().getPath().value(),
                            exchange.getRequest().getURI().getRawQuery());
                    return handleWithCache(exchange, chain, cacheKey, ttl);
                })
                // Route not found in DB — pass through without caching
                .switchIfEmpty(chain.filter(exchange));
    }

    // ── package-private static helper — allows unit testing without Spring ──────

    /**
     * Builds the Redis cache key for a request.
     *
     * <p>Format: {@code sentinel:cache:{routeId}:{path}[?{query}]}
     *
     * @param routeId the matched route identifier
     * @param path    the request path (e.g. {@code /api/orders/42})
     * @param query   the raw query string, or {@code null} / empty string when absent
     * @return the fully qualified Redis cache key
     */
    static String buildCacheKey(String routeId, String path, String query) {
        String querySuffix = (query == null || query.isEmpty()) ? "" : "?" + query;
        return KEY_PREFIX + routeId + ":" + path + querySuffix;
    }

    // ── private helpers ──────────────────────────────────────────────────────────

    private Mono<Void> handleWithCache(ServerWebExchange exchange,
                                       GatewayFilterChain chain,
                                       String cacheKey,
                                       int ttlSeconds) {
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> serveCachedResponse(exchange, cached))
                .switchIfEmpty(
                        proceedAndCache(exchange, chain, cacheKey, ttlSeconds));
    }

    /** Write a previously cached body directly to the response (cache HIT). */
    private Mono<Void> serveCachedResponse(ServerWebExchange exchange, String body) {
        log.debug("Cache HIT for key derived from path={}", exchange.getRequest().getPath());
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add("X-Cache", "HIT");
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.setStatusCode(HttpStatus.OK);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Let the request proceed through the filter chain. If the upstream responds
     * with HTTP 200 OK and the body is within the size limit, store the body in
     * Redis with the given TTL. Adds {@code X-Cache: MISS} to the response.
     */
    private Mono<Void> proceedAndCache(ServerWebExchange exchange,
                                       GatewayFilterChain chain,
                                       String cacheKey,
                                       int ttlSeconds) {
        log.debug("Cache MISS for key derived from path={}", exchange.getRequest().getPath());

        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                HttpStatus status = (HttpStatus) originalResponse.getStatusCode();
                if (status == HttpStatus.OK) {
                    Flux<DataBuffer> bodyFlux = Flux.from(body);
                    return super.writeWith(bodyFlux.collectList().flatMap(dataBuffers -> {
                        // Assemble the full body bytes
                        int totalSize = dataBuffers.stream()
                                .mapToInt(DataBuffer::readableByteCount)
                                .sum();

                        if (totalSize > MAX_CACHEABLE_BYTES) {
                            // Body too large to cache — pass through unchanged
                            log.debug("Response body ({} bytes) exceeds cache limit, skipping cache write", totalSize);
                            byte[] bytes = assembleBytes(dataBuffers, totalSize);
                            return Mono.just(bufferFactory.wrap(bytes));
                        }

                        byte[] bytes = assembleBytes(dataBuffers, totalSize);
                        String bodyStr = new String(bytes, StandardCharsets.UTF_8);

                        // Store in Redis asynchronously; failures are logged but don't fail the request
                        return redisTemplate.opsForValue()
                                .set(cacheKey, bodyStr, Duration.ofSeconds(ttlSeconds))
                                .doOnError(ex -> log.warn("Failed to write cache key {}: {}", cacheKey, ex.getMessage()))
                                .onErrorComplete()
                                .thenReturn(bufferFactory.wrap(bytes));
                    }).flux());
                }
                return super.writeWith(body);
            }
        };

        originalResponse.getHeaders().add("X-Cache", "MISS");

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    /** Reads all DataBuffers into a single byte array and releases the buffers. */
    private static byte[] assembleBytes(java.util.List<? extends DataBuffer> dataBuffers, int totalSize) {
        byte[] bytes = new byte[totalSize];
        int idx = 0;
        for (DataBuffer buf : dataBuffers) {
            int len = buf.readableByteCount();
            buf.read(bytes, idx, len);
            idx += len;
            DataBufferUtils.release(buf);
        }
        return bytes;
    }
}
