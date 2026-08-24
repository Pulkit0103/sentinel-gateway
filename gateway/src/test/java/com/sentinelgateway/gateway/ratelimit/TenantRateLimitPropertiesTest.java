package com.sentinelgateway.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TenantRateLimitProperties default values and configuration binding.
 */
@ExtendWith(MockitoExtension.class)
class TenantRateLimitPropertiesTest {

    // ── Test 1: Default values ────────────────────────────────────────────────

    @Test
    void defaults_enabledIsFalse() {
        TenantRateLimitProperties props = new TenantRateLimitProperties();
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    void defaults_tenantsMapIsEmpty() {
        TenantRateLimitProperties props = new TenantRateLimitProperties();
        assertThat(props.getTenants()).isNotNull();
        assertThat(props.getTenants()).isEmpty();
    }

    // ── Test 2: Mutator / accessor round-trip ────────────────────────────────

    @Test
    void setEnabled_updatesFlag() {
        TenantRateLimitProperties props = new TenantRateLimitProperties();
        props.setEnabled(true);
        assertThat(props.isEnabled()).isTrue();
    }

    @Test
    void setTenants_storesTenantPolicies() {
        TenantRateLimitProperties props = new TenantRateLimitProperties();

        TenantRateLimitProperties.TenantPolicy policy = new TenantRateLimitProperties.TenantPolicy();
        policy.setRequestsPerMinute(5000);

        props.setTenants(Map.of("acme-corp", policy));

        assertThat(props.getTenants()).hasSize(1);
        assertThat(props.getTenants().get("acme-corp").getRequestsPerMinute()).isEqualTo(5000);
    }

    // ── Test 3: TenantPolicy defaults ────────────────────────────────────────

    @Test
    void tenantPolicy_defaultRequestsPerMinute_is1000() {
        TenantRateLimitProperties.TenantPolicy policy = new TenantRateLimitProperties.TenantPolicy();
        assertThat(policy.getRequestsPerMinute()).isEqualTo(1000);
    }
}
