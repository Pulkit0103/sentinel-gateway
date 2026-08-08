package com.sentinelgateway.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AuthenticatedPrincipalTest {

    private final JwtPrincipalExtractor extractor = new JwtPrincipalExtractor();

    // ── AuthenticatedPrincipal record ────────────────────────────────────────

    @Test
    void constructor_allFields_stored() {
        var principal = new AuthenticatedPrincipal(
                "user-123", "tenant-acme",
                List.of("USER", "ADMIN"), List.of("openid", "profile"),
                "sentinel-gateway-client", AuthenticationType.JWT);

        assertThat(principal.userId()).isEqualTo("user-123");
        assertThat(principal.tenantId()).isEqualTo("tenant-acme");
        assertThat(principal.roles()).containsExactly("USER", "ADMIN");
        assertThat(principal.scopes()).containsExactly("openid", "profile");
        assertThat(principal.clientId()).isEqualTo("sentinel-gateway-client");
        assertThat(principal.authenticationType()).isEqualTo(AuthenticationType.JWT);
    }

    @Test
    void constructor_nullUserId_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AuthenticatedPrincipal(
                        null, "tenant", List.of(), List.of(), null, AuthenticationType.JWT));
    }

    @Test
    void constructor_nullListsDefaultToEmpty() {
        var principal = new AuthenticatedPrincipal(
                "user-1", null, null, null, null, AuthenticationType.JWT);
        assertThat(principal.roles()).isEmpty();
        assertThat(principal.scopes()).isEmpty();
    }

    @Test
    void constructor_listsAreImmutable() {
        var principal = new AuthenticatedPrincipal(
                "user-1", null, List.of("ADMIN"), List.of("openid"), null, AuthenticationType.JWT);
        assertThatThrownBy(() -> principal.roles().add("NEW")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> principal.scopes().add("NEW")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hasRole_returnsCorrectly() {
        var principal = new AuthenticatedPrincipal(
                "user-1", null, List.of("USER", "ADMIN"), List.of(), null, AuthenticationType.JWT);
        assertThat(principal.hasRole("ADMIN")).isTrue();
        assertThat(principal.hasRole("SUPPORT")).isFalse();
    }

    @Test
    void hasScope_returnsCorrectly() {
        var principal = new AuthenticatedPrincipal(
                "user-1", null, List.of(), List.of("openid", "profile"), null, AuthenticationType.JWT);
        assertThat(principal.hasScope("profile")).isTrue();
        assertThat(principal.hasScope("write")).isFalse();
    }

    // ── JwtPrincipalExtractor ─────────────────────────────────────────────────

    @Test
    void extract_keycloakRealmRoles_extractedCorrectly() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-abc")
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("tenant_id", "tenant-acme")
                .claim("realm_access", Map.of("roles", List.of("USER", "ADMIN")))
                .claim("scope", "openid profile")
                .claim("azp", "sentinel-gateway-client")
                .build();

        AuthenticatedPrincipal principal = extractor.extract(jwt);

        assertThat(principal.userId()).isEqualTo("user-abc");
        assertThat(principal.tenantId()).isEqualTo("tenant-acme");
        assertThat(principal.roles()).containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(principal.scopes()).containsExactlyInAnyOrder("openid", "profile");
        assertThat(principal.clientId()).isEqualTo("sentinel-gateway-client");
        assertThat(principal.authenticationType()).isEqualTo(AuthenticationType.JWT);
    }

    @Test
    void extract_genericRolesClaim_extractedCorrectly() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-xyz")
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("roles", List.of("SUPPORT"))
                .build();

        AuthenticatedPrincipal principal = extractor.extract(jwt);

        assertThat(principal.roles()).containsExactly("SUPPORT");
        assertThat(principal.tenantId()).isNull();
    }

    @Test
    void extract_noRolesClaim_rolesAreEmpty() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-1")
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        AuthenticatedPrincipal principal = extractor.extract(jwt);

        assertThat(principal.roles()).isEmpty();
        assertThat(principal.scopes()).isEmpty();
        assertThat(principal.clientId()).isNull();
    }

    @Test
    void extract_scpClaimAsList_extractedCorrectly() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("service-account")
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("scp", List.of("read", "write"))
                .build();

        AuthenticatedPrincipal principal = extractor.extract(jwt);

        assertThat(principal.scopes()).containsExactlyInAnyOrder("read", "write");
    }
}
