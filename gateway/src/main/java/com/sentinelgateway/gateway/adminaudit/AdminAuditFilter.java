package com.sentinelgateway.gateway.adminaudit;

import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * WebFilter that records each /admin/** request in {@link AdminAuditRegistry} (Phase 31).
 *
 * Runs at LOWEST_PRECEDENCE - 1, after the chain, so the HTTP status is known.
 * Subject is extracted from the JWT; anonymous or non-JWT callers are recorded as "anonymous".
 * The /admin/audit-log endpoint itself is excluded to avoid infinite recursion.
 */
@Component
public class AdminAuditFilter implements WebFilter, Ordered {

    public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 1;

    private final AdminAuditRegistry registry;

    public AdminAuditFilter(AdminAuditRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/admin") || path.startsWith("/admin/audit-log")) {
            return chain.filter(exchange);
        }

        String method = exchange.getRequest().getMethod().name();

        return chain.filter(exchange)
                .then(ReactiveSecurityContextHolder.getContext()
                        .map(ctx -> {
                            if (ctx.getAuthentication() instanceof JwtAuthenticationToken jwtAuth) {
                                return jwtAuth.getToken().getSubject() != null
                                        ? jwtAuth.getToken().getSubject()
                                        : "anonymous";
                            }
                            return "anonymous";
                        })
                        .defaultIfEmpty("anonymous")
                        .doOnNext(subject -> {
                            int status = exchange.getResponse().getStatusCode() != null
                                    ? exchange.getResponse().getStatusCode().value()
                                    : 0;
                            registry.record(new AdminAuditRecord(
                                    Instant.now(), subject, method, path, status));
                        })
                        .then());
    }
}
