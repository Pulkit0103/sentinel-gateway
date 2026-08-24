package com.sentinelgateway.gateway.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtClaimsForwardingProperties}.
 *
 * Verifies default values and basic mutator behaviour without needing a
 * Spring application context.
 */
class JwtClaimsForwardingPropertiesTest {

    @Test
    void defaults_enabledIsFalse() {
        var props = new JwtClaimsForwardingProperties();
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    void defaults_mappingsIsEmptyList() {
        var props = new JwtClaimsForwardingProperties();
        assertThat(props.getMappings()).isNotNull().isEmpty();
    }

    @Test
    void setEnabled_updatesFlag() {
        var props = new JwtClaimsForwardingProperties();
        props.setEnabled(true);
        assertThat(props.isEnabled()).isTrue();
    }

    @Test
    void setMappings_storesMappings() {
        var props = new JwtClaimsForwardingProperties();

        var m = new JwtClaimsForwardingProperties.ClaimMapping();
        m.setClaim("email");
        m.setHeader("X-User-Email");

        props.setMappings(List.of(m));

        assertThat(props.getMappings()).hasSize(1);
        assertThat(props.getMappings().get(0).getClaim()).isEqualTo("email");
        assertThat(props.getMappings().get(0).getHeader()).isEqualTo("X-User-Email");
    }
}
