package com.sentinelgateway.gateway.policy;

import org.springframework.http.HttpStatus;

/**
 * Represents a security policy violation that stops request processing.
 *
 * Each {@link PolicyCheck} returns either {@code Mono.empty()} (no violation)
 * or {@code Mono.just(violation)} (deny the request with the given status).
 */
public record PolicyViolation(HttpStatus status, String reason) {}
