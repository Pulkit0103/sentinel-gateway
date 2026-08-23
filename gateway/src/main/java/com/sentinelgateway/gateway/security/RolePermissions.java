package com.sentinelgateway.gateway.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sentinelgateway.gateway.security.Permission.*;

/**
 * Static role-to-permission mapping.
 *
 * Roles (coarse-grained, from Keycloak realm roles) expand to permissions
 * (fine-grained, used in route {@code requiredScopes}). Routes that require a
 * permission are accessible to any principal whose role grants that permission.
 *
 * Role definitions:
 *   ADMIN   — unrestricted access to all resources
 *   USER    — own-data access: read/write users and orders; no payment access
 *   SUPPORT — read-only cross-tenant visibility; no write access
 *   SERVICE — full access for machine-to-machine service accounts
 */
public final class RolePermissions {

    private static final Set<Permission> ALL = Set.of(Permission.values());

    public static final Map<String, Set<Permission>> ROLE_TO_PERMISSIONS = Map.of(
            "ADMIN",   ALL,
            "SERVICE", ALL,
            "USER",    Set.of(USER_READ, USER_WRITE, ORDER_READ, ORDER_WRITE),
            "SUPPORT", Set.of(USER_READ, ORDER_READ, PAYMENT_READ)
    );

    private RolePermissions() {}

    /**
     * Returns the union of permissions granted by all supplied roles.
     * Unknown roles contribute no permissions.
     */
    public static Set<String> effectivePermissionNames(Collection<String> roles) {
        return roles.stream()
                .flatMap(role -> ROLE_TO_PERMISSIONS.getOrDefault(role, Set.of()).stream())
                .map(Permission::name)
                .collect(Collectors.toUnmodifiableSet());
    }
}
