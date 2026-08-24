package com.sentinelgateway.gateway.session;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Admin endpoint exposing active JWT session data.
 *
 * GET  /admin/sessions                          — active sessions in last 5 minutes
 * GET  /admin/sessions?windowSeconds=N          — active sessions in last N seconds
 * POST /admin/sessions/evict?olderThanSeconds=N — remove stale entries older than N seconds
 *
 * All endpoints require ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin/sessions")
public class SessionController {

    private static final long DEFAULT_WINDOW_SECONDS = 300; // 5 minutes

    private final ActiveSessionRegistry registry;

    public SessionController(ActiveSessionRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public Mono<Map<String, Object>> activeSessions(
            @RequestParam(name = "windowSeconds", defaultValue = "300") long windowSeconds) {
        Instant since = Instant.now().minusSeconds(windowSeconds);
        Set<String> active = registry.activeSince(since);
        return Mono.just(Map.of(
                "windowSeconds", windowSeconds,
                "activeSessions", active.size(),
                "subjects", active,
                "totalTracked", registry.totalTracked()
        ));
    }

    @PostMapping("/evict")
    public Mono<Map<String, Object>> evictStale(
            @RequestParam(name = "olderThanSeconds", defaultValue = "3600") long olderThanSeconds) {
        int before = registry.totalTracked();
        registry.evictBefore(Instant.now().minusSeconds(olderThanSeconds));
        int after = registry.totalTracked();
        return Mono.just(Map.of(
                "evicted", before - after,
                "remaining", after
        ));
    }
}
