package com.sentinelgateway.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SecurityHeadersProperties} (Phase 29: Response Security Headers).
 */
class SecurityHeadersPropertiesTest {

    private SecurityHeadersProperties props;

    @BeforeEach
    void setUp() {
        props = new SecurityHeadersProperties();
    }

    @Test
    void defaults_enabled_and_fiveSecurityHeaders() {
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.effectiveHeaders())
                .containsKey("X-Content-Type-Options")
                .containsKey("X-Frame-Options")
                .containsKey("Referrer-Policy")
                .containsKey("X-XSS-Protection")
                .containsKey("Permissions-Policy");
    }

    @Test
    void xContentTypeOptions_defaultValue_isNosniff() {
        assertThat(props.effectiveHeaders().get("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void referrerPolicy_defaultValue_isStrictOrigin() {
        assertThat(props.effectiveHeaders().get("Referrer-Policy"))
                .isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    void customHeader_added_appearsInEffective() {
        Map<String, String> custom = new LinkedHashMap<>(props.getHeaders());
        custom.put("Cross-Origin-Opener-Policy", "same-origin");
        props.setHeaders(custom);
        assertThat(props.effectiveHeaders()).containsKey("Cross-Origin-Opener-Policy");
    }

    @Test
    void blankValue_removesHeaderFromEffective() {
        Map<String, String> custom = new LinkedHashMap<>(props.getHeaders());
        custom.put("Permissions-Policy", "");
        props.setHeaders(custom);
        assertThat(props.effectiveHeaders()).doesNotContainKey("Permissions-Policy");
    }

    @Test
    void enabled_canBeToggled() {
        props.setEnabled(false);
        assertThat(props.isEnabled()).isFalse();
    }
}
