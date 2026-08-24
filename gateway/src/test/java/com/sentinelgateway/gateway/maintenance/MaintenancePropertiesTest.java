package com.sentinelgateway.gateway.maintenance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MaintenanceProperties} (Phase 18: Maintenance Mode).
 */
class MaintenancePropertiesTest {

    @Test
    void defaults_enabledIsFalse() {
        assertThat(new MaintenanceProperties().isEnabled()).isFalse();
    }

    @Test
    void defaults_messageIsSet() {
        assertThat(new MaintenanceProperties().getMessage())
                .isNotBlank()
                .contains("maintenance");
    }

    @Test
    void defaults_retryAfterSeconds_is60() {
        assertThat(new MaintenanceProperties().getRetryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void defaults_bypassRoles_containsRoleAdmin() {
        assertThat(new MaintenanceProperties().getBypassRoles()).contains("ROLE_ADMIN");
    }

    @Test
    void setEnabled_updatesFlag() {
        MaintenanceProperties props = new MaintenanceProperties();
        props.setEnabled(true);
        assertThat(props.isEnabled()).isTrue();
    }

    @Test
    void setMessage_updatesMessage() {
        MaintenanceProperties props = new MaintenanceProperties();
        props.setMessage("Down for upgrade");
        assertThat(props.getMessage()).isEqualTo("Down for upgrade");
    }

    @Test
    void setRetryAfterSeconds_updatesValue() {
        MaintenanceProperties props = new MaintenanceProperties();
        props.setRetryAfterSeconds(120);
        assertThat(props.getRetryAfterSeconds()).isEqualTo(120);
    }

    @Test
    void setBypassRoles_storesRoles() {
        MaintenanceProperties props = new MaintenanceProperties();
        props.setBypassRoles(List.of("ROLE_ADMIN", "ROLE_OPS"));
        assertThat(props.getBypassRoles()).containsExactly("ROLE_ADMIN", "ROLE_OPS");
    }
}
