package com.sentinelgateway.gateway.policy;

import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;

/**
 * Immutable snapshot of everything a {@link PolicyCheck} needs to make its decision.
 *
 * Passing context as a record keeps individual checks dependency-free: each
 * check receives exactly the state it needs, with no shared mutable state.
 */
public record PolicyEvaluationContext(
        ServerWebExchange exchange,
        SecurityPolicy policy,
        Authentication authentication   // null when request is unauthenticated
) {}
