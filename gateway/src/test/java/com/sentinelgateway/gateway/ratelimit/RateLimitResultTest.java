package com.sentinelgateway.gateway.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitResultTest {

    @Test
    void allowed_setsFieldsCorrectly() {
        var result = RateLimitResult.allowed(1000, 500, 45);
        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(1000);
        assertThat(result.remaining()).isEqualTo(500);
        assertThat(result.resetAfterSecs()).isEqualTo(45);
    }

    @Test
    void denied_setsRemainingToZero() {
        var result = RateLimitResult.denied(100, 30);
        assertThat(result.allowed()).isFalse();
        assertThat(result.limit()).isEqualTo(100);
        assertThat(result.remaining()).isZero();
        assertThat(result.resetAfterSecs()).isEqualTo(30);
    }
}
