package com.sentinelgateway.gateway.signing;

import reactor.core.publisher.Mono;

/**
 * Replay prevention via nonce tracking.
 *
 * A nonce is a unique value sent with each signed request. Once a nonce is
 * seen, it is stored until the nonce TTL expires. Reusing a nonce within the
 * TTL window returns false (replay detected).
 */
public interface NonceStore {

    /**
     * Records the nonce and returns true if it is fresh (first use within TTL).
     * Returns false if the nonce has already been seen (replay attack).
     *
     * @param nonce  the nonce value from the request
     * @param ttlSec how long to remember this nonce
     */
    Mono<Boolean> recordIfAbsent(String nonce, long ttlSec);
}
