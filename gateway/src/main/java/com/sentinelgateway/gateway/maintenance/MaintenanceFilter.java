package com.sentinelgateway.gateway.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * WebFilter that enforces maintenance mode.
 *
 * When {@link MaintenanceProperties#isEnabled()} is true, all requests that:
 * - are NOT under /actuator/** or /admin/**   (operational paths stay reachable)
 * - do NOT carry a role listed in bypassRoles (default: ROLE_ADMIN)
 *
 * receive a 503 Service Unavailable with a JSON body and a {@code Retry-After} header.
 *
 * Implemented as a {@link WebFilter} (not a GlobalFilter) so it runs before
 * Spring Cloud Gateway's route matching and can intercept any HTTP path, including
 * paths that match no gateway route.
 */
@Component
public class MaintenanceFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceFilter.class);

    /** Before ALL gateway filters; operational paths are whitelisted first. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 2;

    private final MaintenanceProperties maintenanceProperties;

    public MaintenanceFilter(MaintenanceProperties maintenanceProperties) {
        this.maintenanceProperties = maintenanceProperties;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!maintenanceProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();

        // Operational paths always pass through so health probes and admin controls work.
        if (path.startsWith("/actuator") || path.startsWith("/admin")) {
            return chain.filter(exchange);
        }

        // Check if the authenticated principal holds a bypass role.
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> {
                    if (ctx.getAuthentication() == null) return false;
                    return ctx.getAuthentication().getAuthorities().stream()
                            .anyMatch(a -> maintenanceProperties.getBypassRoles()
                                    .contains(a.getAuthority()));
                })
                .defaultIfEmpty(false)
                .flatMap(bypass -> {
                    if (bypass) {
                        log.debug("Maintenance bypass granted for {}", path);
                        return chain.filter(exchange);
                    }
                    return sendMaintenanceResponse(exchange);
                });
    }

    private Mono<Void> sendMaintenanceResponse(ServerWebExchange exchange) {
        log.info("Maintenance mode: returning 503 for {}", exchange.getRequest().getPath());
        int retryAfter = maintenanceProperties.getRetryAfterSeconds();
        String body = String.format(
                "{\"status\":\"maintenance\",\"message\":\"%s\",\"retryAfterSeconds\":%d}",
                maintenanceProperties.getMessage().replace("\"", "\\\""),
                retryAfter);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", String.valueOf(retryAfter));
        response.getHeaders().setContentLength(bytes.length);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
