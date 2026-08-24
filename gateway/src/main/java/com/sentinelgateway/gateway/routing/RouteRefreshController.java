package com.sentinelgateway.gateway.routing;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Admin endpoint for manually triggering a gateway route refresh.
 *
 * Publishing a {@link RefreshRoutesEvent} causes {@code CachingRouteLocator} to
 * invalidate its cache and re-fetch routes from {@link SentinelRouteDefinitionRepository}.
 * Under normal operation this happens automatically after every Admin API mutation
 * (create / update / delete) via {@link RouteService}. This endpoint is provided
 * for operators who need to force a refresh without making a route change —
 * for example after an external configuration change.
 *
 * Secured by {@code ROLE_ADMIN} via the existing {@code SecurityWebFilterChain}.
 */
@RestController
@RequestMapping("/admin/routes/refresh")
public class RouteRefreshController {

    private final ApplicationEventPublisher eventPublisher;

    public RouteRefreshController(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Manually trigger a route refresh.
     *
     * @return 204 No Content on success
     */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> refresh() {
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
        return Mono.empty();
    }
}
