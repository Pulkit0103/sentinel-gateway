package com.sentinelgateway.gateway.lifecycle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DrainProperties} — verifies defaults and mutators.
 */
class DrainPropertiesTest {

    // ── Test 1: Default values ────────────────────────────────────────────────

    @Test
    void defaults_enabledIsTrue() {
        DrainProperties props = new DrainProperties();
        assertThat(props.isEnabled()).isTrue();
    }

    @Test
    void defaults_timeoutSecondsIs30() {
        DrainProperties props = new DrainProperties();
        assertThat(props.getTimeoutSeconds()).isEqualTo(30);
    }

    // ── Test 2: Mutator / accessor round-trip ────────────────────────────────

    @Test
    void setEnabled_updatesFlag() {
        DrainProperties props = new DrainProperties();
        props.setEnabled(false);
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    void setTimeoutSeconds_updatesValue() {
        DrainProperties props = new DrainProperties();
        props.setTimeoutSeconds(60);
        assertThat(props.getTimeoutSeconds()).isEqualTo(60);
    }
}
