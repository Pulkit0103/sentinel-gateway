package com.sentinelgateway.gateway.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class RolePermissionsTest {

    @Test
    void adminRole_getsAllPermissions() {
        Set<String> permissions = RolePermissions.effectivePermissionNames(List.of("ADMIN"));
        assertThat(permissions).contains("USER_READ", "USER_WRITE", "ORDER_READ", "ORDER_WRITE",
                "PAYMENT_READ", "PAYMENT_WRITE", "ADMIN_ALL");
    }

    @Test
    void userRole_getsUserAndOrderPermissions_notPayment() {
        Set<String> permissions = RolePermissions.effectivePermissionNames(List.of("USER"));
        assertThat(permissions).contains("USER_READ", "USER_WRITE", "ORDER_READ", "ORDER_WRITE");
        assertThat(permissions).doesNotContain("PAYMENT_READ", "PAYMENT_WRITE");
    }

    @Test
    void supportRole_getsReadPermissions_notWrite() {
        Set<String> permissions = RolePermissions.effectivePermissionNames(List.of("SUPPORT"));
        assertThat(permissions).contains("USER_READ", "ORDER_READ", "PAYMENT_READ");
        assertThat(permissions).doesNotContain("USER_WRITE", "ORDER_WRITE", "PAYMENT_WRITE");
    }

    @Test
    void serviceRole_getsAllPermissions() {
        Set<String> permissions = RolePermissions.effectivePermissionNames(List.of("SERVICE"));
        assertThat(permissions).contains("USER_READ", "USER_WRITE", "PAYMENT_READ", "PAYMENT_WRITE");
    }

    @Test
    void unknownRole_getsNoPermissions() {
        Set<String> permissions = RolePermissions.effectivePermissionNames(List.of("GUEST", "DEVELOPER"));
        assertThat(permissions).isEmpty();
    }

    @Test
    void emptyRoles_getsNoPermissions() {
        Set<String> permissions = RolePermissions.effectivePermissionNames(List.of());
        assertThat(permissions).isEmpty();
    }

    @Test
    void multipleRoles_unionsPermissions() {
        // USER does not have PAYMENT_READ; SUPPORT does. Combined they get both.
        Set<String> permissions = RolePermissions.effectivePermissionNames(List.of("USER", "SUPPORT"));
        assertThat(permissions).contains("USER_READ", "USER_WRITE", "ORDER_READ", "PAYMENT_READ");
    }
}
