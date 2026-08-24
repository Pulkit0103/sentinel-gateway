package com.sentinelgateway.gateway.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin endpoints for toggling maintenance mode at runtime.
 *
 * All paths under /admin/** require ROLE_ADMIN (enforced by SecurityWebFilterChain).
 * The MaintenanceFilter explicitly skips /admin/** so these endpoints remain
 * reachable even when maintenance mode is active.
 */
@RestController
@RequestMapping("/admin/maintenance")
public class MaintenanceController {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceController.class);

    private final MaintenanceProperties maintenanceProperties;

    public MaintenanceController(MaintenanceProperties maintenanceProperties) {
        this.maintenanceProperties = maintenanceProperties;
    }

    @GetMapping
    public Mono<Map<String, Object>> status() {
        return Mono.just(buildStatusBody());
    }

    @PostMapping("/enable")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, Object>> enable() {
        log.warn("Maintenance mode ENABLED by admin request");
        maintenanceProperties.setEnabled(true);
        return Mono.just(buildStatusBody());
    }

    @PostMapping("/disable")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, Object>> disable() {
        log.info("Maintenance mode DISABLED by admin request");
        maintenanceProperties.setEnabled(false);
        return Mono.just(buildStatusBody());
    }

    private Map<String, Object> buildStatusBody() {
        return Map.of(
                "enabled", maintenanceProperties.isEnabled(),
                "message", maintenanceProperties.getMessage(),
                "retryAfterSeconds", maintenanceProperties.getRetryAfterSeconds()
        );
    }
}
