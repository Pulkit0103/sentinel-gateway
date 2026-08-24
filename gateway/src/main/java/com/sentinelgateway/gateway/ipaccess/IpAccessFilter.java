package com.sentinelgateway.gateway.ipaccess;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * WebFilter that enforces IP-based access control (Phase 28).
 *
 * Runs at HIGHEST_PRECEDENCE + 2 — early in the filter chain, before routing.
 * Extracts the client IP from X-Forwarded-For (first entry) or the remote address.
 * In ALLOWLIST mode: denies requests from IPs not in the global list.
 * In DENYLIST mode: denies requests from IPs in the global list.
 *
 * Per-route overrides (routes map) are available for future path-based matching.
 */
@Component
public class IpAccessFilter implements WebFilter, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 2;

    private final IpAccessProperties properties;

    public IpAccessFilter(IpAccessProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(exchange);
        List<String> ipList = properties.getGlobalList();

        boolean inList = ipList.contains(clientIp);
        boolean denied = (properties.getMode() == IpAccessProperties.Mode.ALLOWLIST) ? !inList : inList;

        if (denied) {
            return sendForbidden(exchange, clientIp);
        }
        return chain.filter(exchange);
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote != null) {
            return remote.getAddress().getHostAddress();
        }
        return "unknown";
    }

    private Mono<Void> sendForbidden(ServerWebExchange exchange, String clientIp) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"status\":403,\"error\":\"IP access denied\",\"ip\":\"%s\",\"mode\":\"%s\"}",
                clientIp, properties.getMode());
        DataBuffer buf = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buf));
    }
}
