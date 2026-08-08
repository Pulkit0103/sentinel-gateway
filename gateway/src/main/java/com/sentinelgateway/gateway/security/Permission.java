package com.sentinelgateway.gateway.security;

/**
 * Fine-grained permissions enforced at the route level.
 * Routes declare which permissions are required via {@code requiredScopes}.
 *
 * Permissions can arrive in a JWT in two ways:
 *   1. Directly in the {@code scope} claim (OAuth2 token exchange)
 *   2. Implicitly via the caller's role (resolved by {@link RolePermissions})
 */
public enum Permission {

    // User resource
    USER_READ,
    USER_WRITE,

    // Order resource
    ORDER_READ,
    ORDER_WRITE,

    // Payment resource
    PAYMENT_READ,
    PAYMENT_WRITE,

    // Administrative operations
    ADMIN_ALL
}
