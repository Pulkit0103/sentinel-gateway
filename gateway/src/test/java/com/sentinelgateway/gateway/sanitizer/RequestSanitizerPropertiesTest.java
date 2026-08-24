package com.sentinelgateway.gateway.sanitizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RequestSanitizerProperties} (Phase 32: Request Header Sanitizer).
 */
class RequestSanitizerPropertiesTest {

    private RequestSanitizerProperties props;

    @BeforeEach
    void setUp() {
        props = new RequestSanitizerProperties();
    }

    @Test
    void defaults_enabled_8192_blockNullBytes() {
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getMaxHeaderValueLength()).isEqualTo(8192);
        assertThat(props.isBlockNullBytes()).isTrue();
    }

    @Test
    void maxHeaderValueLength_canBeChanged() {
        props.setMaxHeaderValueLength(256);
        assertThat(props.getMaxHeaderValueLength()).isEqualTo(256);
    }

    @Test
    void blockNullBytes_canBeDisabled() {
        props.setBlockNullBytes(false);
        assertThat(props.isBlockNullBytes()).isFalse();
    }

    @Test
    void enabled_canBeToggled() {
        props.setEnabled(false);
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    void isSkipped_standardHeaders_returnTrue() {
        assertThat(props.isSkipped("Authorization")).isTrue();
        assertThat(props.isSkipped("authorization")).isTrue();  // case-insensitive
        assertThat(props.isSkipped("User-Agent")).isTrue();
        assertThat(props.isSkipped("Content-Type")).isTrue();
    }

    @Test
    void isSkipped_customHeaders_returnFalse() {
        assertThat(props.isSkipped("X-Custom-Header")).isFalse();
        assertThat(props.isSkipped("X-Oversized")).isFalse();
    }
}
