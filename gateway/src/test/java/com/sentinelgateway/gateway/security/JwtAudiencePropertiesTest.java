package com.sentinelgateway.gateway.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtAudienceProperties} (Phase 22: JWT Audience Validation).
 */
class JwtAudiencePropertiesTest {

    @Test
    void defaults_requiredAudiencesIsEmpty() {
        assertThat(new JwtAudienceProperties().getRequiredAudiences()).isEmpty();
    }

    @Test
    void defaults_audienceValidationDisabled() {
        assertThat(new JwtAudienceProperties().isAudienceValidationEnabled()).isFalse();
    }

    @Test
    void setRequiredAudiences_enablesValidation() {
        JwtAudienceProperties props = new JwtAudienceProperties();
        props.setRequiredAudiences(List.of("sentinel-gateway"));
        assertThat(props.isAudienceValidationEnabled()).isTrue();
    }

    @Test
    void setRequiredAudiences_storesList() {
        JwtAudienceProperties props = new JwtAudienceProperties();
        props.setRequiredAudiences(List.of("audience-a", "audience-b"));
        assertThat(props.getRequiredAudiences()).containsExactly("audience-a", "audience-b");
    }

    @Test
    void emptyAudienceList_validationDisabled() {
        JwtAudienceProperties props = new JwtAudienceProperties();
        props.setRequiredAudiences(List.of());
        assertThat(props.isAudienceValidationEnabled()).isFalse();
    }
}
