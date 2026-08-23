package com.sentinelgateway.gateway.signing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Verifies HMAC-SHA256 request signatures for signed requests.
 *
 * When all four HMAC headers are present the filter verifies the signature,
 * timestamp freshness, and nonce uniqueness. On success it injects an
 * HmacAuthentication into the Reactor security context so Spring Security
 * treats the request as authenticated.
 *
 * When HMAC headers are absent the filter is a no-op — JWT/API-key auth runs.
 *
 * Required headers (all four required together):
 *   X-Client-Id   — caller identity (included in canonical message)
 *   X-Timestamp   — Unix epoch seconds (must be within tolerance window)
 *   X-Nonce       — unique per-request value (replay prevention)
 *   X-Signature   — HMAC-SHA256 hex of canonical message
 *
 * Canonical message: {METHOD}\n{path}\n{timestamp}\n{nonce}\n{sha256(body)}
 *
 * Runs before Spring Security (order HIGHEST_PRECEDENCE + 8).
 * Body is read entirely to compute its hash, then re-wrapped for downstream.
 *
 * Reactor safety: defaultIfEmpty + flatMap, never switchIfEmpty after Mono<Void>.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 8)
@ConditionalOnProperty(name = "sentinel.request-signing.enabled", havingValue = "true")
public class HmacVerificationFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(HmacVerificationFilter.class);

    public static final String HEADER_CLIENT_ID = "X-Client-Id";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE     = "X-Nonce";
    public static final String HEADER_SIGNATURE = "X-Signature";

    private final HmacSigningConfig config;
    private final HmacSigner signer;
    private final NonceStore nonceStore;

    public HmacVerificationFilter(HmacSigningConfig config, HmacSigner signer, NonceStore nonceStore) {
        this.config = config;
        this.signer = signer;
        this.nonceStore = nonceStore;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String clientId  = req.getHeaders().getFirst(HEADER_CLIENT_ID);
        String timestamp = req.getHeaders().getFirst(HEADER_TIMESTAMP);
        String nonce     = req.getHeaders().getFirst(HEADER_NONCE);
        String signature = req.getHeaders().getFirst(HEADER_SIGNATURE);

        // No HMAC headers — pass through to JWT/API key auth
        if (clientId == null && timestamp == null && nonce == null && signature == null) {
            return chain.filter(exchange);
        }

        // Partial HMAC headers — reject immediately
        if (clientId == null || timestamp == null || nonce == null || signature == null) {
            return reject(exchange, "Incomplete request signing headers");
        }

        // Timestamp freshness
        long epochNow = Instant.now().getEpochSecond();
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return reject(exchange, "Invalid timestamp format");
        }
        if (Math.abs(epochNow - requestTime) > config.getTimestampToleranceSeconds()) {
            return reject(exchange, "Timestamp outside tolerance window");
        }

        // Read body, verify, check nonce, continue
        final String finalTimestamp = timestamp;
        final String finalNonce = nonce;
        final String finalSignature = signature;
        final String finalClientId = clientId;

        return DataBufferUtils.join(req.getBody())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0])
                .flatMap(bodyBytes -> {
                    String expected = signer.sign(
                            config.getSharedSecret(),
                            req.getMethod().name(),
                            req.getPath().value(),
                            finalTimestamp, finalNonce, bodyBytes);

                    if (!signer.verify(expected, finalSignature)) {
                        return reject(exchange, "Signature mismatch");
                    }

                    return nonceStore.recordIfAbsent(finalNonce, config.getNonceTtlSeconds())
                            .map(fresh -> (Object) fresh)
                            .defaultIfEmpty(Boolean.FALSE)
                            .flatMap(obj -> {
                                if (obj instanceof Boolean fresh && !fresh) {
                                    return reject(exchange, "Nonce already used (replay)");
                                }
                                HmacAuthentication auth = new HmacAuthentication(finalClientId);
                                ServerHttpRequest mutated = new BodyCachingRequest(req, bodyBytes);
                                return chain.filter(exchange.mutate().request(mutated).build())
                                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                            });
                });
    }

    private Mono<Void> reject(ServerWebExchange exchange, String reason) {
        log.warn("HMAC verification failed: {}", reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private static class BodyCachingRequest extends ServerHttpRequestDecorator {
        private final byte[] bodyBytes;

        BodyCachingRequest(ServerHttpRequest delegate, byte[] bodyBytes) {
            super(delegate);
            this.bodyBytes = bodyBytes;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bodyBytes));
        }
    }
}
