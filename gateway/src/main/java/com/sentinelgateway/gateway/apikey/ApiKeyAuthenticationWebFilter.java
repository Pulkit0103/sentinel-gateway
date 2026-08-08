package com.sentinelgateway.gateway.apikey;

import com.sentinelgateway.gateway.security.AuthenticatedPrincipal;
import com.sentinelgateway.gateway.security.AuthenticationType;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that authenticates requests carrying an X-API-Key header.
 *
 * Runs before Spring Security's OAuth2 resource server filter (order -2).
 * If a valid API key is found it injects an ApiKeyAuthentication into the reactive
 * security context and the downstream JWT filter is effectively skipped.
 * If the header is absent this filter is a no-op — the JWT path proceeds normally.
 * If the header is present but invalid/revoked/expired → 401 immediately.
 *
 * Reactor safety: always uses defaultIfEmpty + flatMap; never switchIfEmpty after Mono<Void>.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ApiKeyAuthenticationWebFilter implements WebFilter {

    static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationWebFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String rawKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            return chain.filter(exchange); // no API key header — proceed to JWT path
        }

        return apiKeyService.validate(rawKey)
                .map(apiKey -> {
                    AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                            apiKey.getClientId(),
                            apiKey.getTenantId(),
                            List.of(),
                            apiKey.scopeList(),
                            apiKey.getClientId(),
                            AuthenticationType.API_KEY
                    );
                    return new ApiKeyAuthentication(principal);
                })
                .map(auth -> (Object) auth)
                .defaultIfEmpty(Boolean.FALSE)
                .flatMap(result -> {
                    if (result instanceof ApiKeyAuthentication auth) {
                        return chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                    }
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }
}
