package com.sentinelgateway.gateway.admin;

import com.sentinelgateway.gateway.routing.RouteDefinition;
import com.sentinelgateway.gateway.routing.RouteRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/admin/routes")
public class RouteAdminController {

    private final RouteRegistry routeRegistry;

    public RouteAdminController(RouteRegistry routeRegistry) {
        this.routeRegistry = routeRegistry;
    }

    @GetMapping
    public Flux<RouteResponse> listRoutes() {
        return Flux.fromIterable(routeRegistry.allRoutes()).map(RouteResponse::from);
    }

    record RouteResponse(String routeId, String path, String serviceUri,
                         List<String> methods, boolean enabled,
                         List<String> requiredScopes, boolean tenantRequired,
                         String rateLimitPolicy) {
        static RouteResponse from(RouteDefinition d) {
            return new RouteResponse(d.routeId(), d.path(), d.serviceUri(),
                    d.methods(), d.enabled(), d.requiredScopes(),
                    d.tenantRequired(), d.rateLimitPolicy());
        }
    }
}
