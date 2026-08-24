package com.sentinelgateway.gateway.ipfilter;

import com.sentinelgateway.gateway.routing.RouteRepository;
import com.sentinelgateway.gateway.webhook.WebhookEvent;
import com.sentinelgateway.gateway.webhook.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * GlobalFilter that enforces per-route IP allowlists and denylists.
 *
 * Runs at {@code Ordered.HIGHEST_PRECEDENCE + 1} — earlier than all other GlobalFilters
 * (JwtRevocationFilter is at +5, JwtHeadersFilter at +10). At this order, Spring Cloud
 * Gateway has already matched the route (via RoutePredicateHandlerMapping), so
 * {@code GATEWAY_ROUTE_ATTR} is populated and we can look up the route's IP config.
 *
 * Client IP resolution order:
 * 1. First value of {@code X-Forwarded-For} header (trusted reverse-proxy header)
 * 2. Remote socket address ({@code exchange.getRequest().getRemoteAddress()})
 * 3. Fallback string {@code "unknown"}
 *
 * Decision logic (delegated to {@link IpFilterService}):
 * - If route has an allowlist and client IP is not in it → 403
 * - If route has a denylist and client IP is in it → 403
 * - Otherwise → allow
 *
 * If the route is not found in the database (e.g., dynamically removed), the filter
 * allows the request through (fail-open to avoid blocking legitimate traffic during
 * DB hiccups).
 */
@Component
public class IpFilterGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(IpFilterGlobalFilter.class);

    private final IpFilterService ipFilterService;
    private final IpFilterProperties ipFilterProperties;
    private final RouteRepository routeRepository;
    private final WebhookService webhookService;

    public IpFilterGlobalFilter(IpFilterService ipFilterService,
                                IpFilterProperties ipFilterProperties,
                                RouteRepository routeRepository,
                                WebhookService webhookService) {
        this.ipFilterService = ipFilterService;
        this.ipFilterProperties = ipFilterProperties;
        this.routeRepository = routeRepository;
        this.webhookService = webhookService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!ipFilterProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return chain.filter(exchange);
        }

        String routeId = route.getId();
        String clientIp = getClientIp(exchange);

        // Resolve boolean Mono<proceed>, then call chain exactly once.
        // Using the thenReturn/defaultIfEmpty pattern avoids the Mono<Void>
        // double-subscription bug that occurs with switchIfEmpty(chain.filter()).
        return routeRepository.findByRouteId(routeId)
                .flatMap(entity -> {
                    boolean allowed = ipFilterService.isAllowed(
                            clientIp, entity.allowedIpList(), entity.blockedIpList());
                    if (!allowed) {
                        log.warn("IP {} blocked on route {} (allowed={}, blocked={})",
                                clientIp, routeId, entity.getAllowedIps(), entity.getBlockedIps());
                        // Fire-and-forget webhook emission — does not block the response
                        String requestId = UUID.randomUUID().toString();
                        String path = exchange.getRequest().getPath().value();
                        WebhookEvent event = new WebhookEvent(
                                "ROUTE_BLOCKED", requestId, clientIp, path, routeId,
                                Instant.now(), Map.of());
                        webhookService.emit(event).subscribe(
                                null,
                                err -> log.warn("Webhook emit failed: {}", err.getMessage())
                        );
                        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                        return exchange.getResponse().setComplete().thenReturn(false);
                    }
                    return Mono.just(true);
                })
                .defaultIfEmpty(true)
                .flatMap(proceed -> proceed ? chain.filter(exchange) : Mono.empty());
    }

    /**
     * Extracts the client IP, preferring {@code X-Forwarded-For} for proxy-aware deployments.
     */
    private String getClientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        var addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
