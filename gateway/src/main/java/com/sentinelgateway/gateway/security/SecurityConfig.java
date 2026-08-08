package com.sentinelgateway.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configures the gateway as an OAuth2 Resource Server.
 *
 * Security policy:
 * - /actuator/** — public (health probes, metrics scraping)
 * - /admin/**   — ADMIN role required (Phase 17)
 * - all other paths — require a valid Bearer JWT
 *
 * JWT validation chain (in order):
 * 1. Signature — verified against the JWKS endpoint (Keycloak public keys)
 * 2. Expiry (exp) and not-before (nbf) — JwtTimestampValidator
 * 3. Issuer (iss) — must match sentinel.security.jwt.issuer if configured
 *
 * Role mapping: Keycloak puts roles in realm_access.roles (a nested object).
 * jwtAuthenticationConverter() extracts them and maps each to ROLE_<UPPERCASE>
 * so Spring Security's hasRole("ADMIN") checks work correctly.
 */
@Configuration
@EnableWebFluxSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/admin/**").hasRole("ADMIN")
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }

    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) return Flux.empty();
            Object rolesObj = realmAccess.get("roles");
            if (!(rolesObj instanceof List<?> roles)) return Flux.empty();
            return Flux.fromStream(
                    roles.stream()
                            .filter(r -> r instanceof String)
                            .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(
                                    "ROLE_" + ((String) r).toUpperCase()))
            );
        });
        return converter;
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwksUri,
            @Value("${sentinel.security.jwt.issuer:}") String issuer) {

        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        if (!issuer.isBlank()) {
            validators.add(new JwtIssuerValidator(issuer));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }
}
