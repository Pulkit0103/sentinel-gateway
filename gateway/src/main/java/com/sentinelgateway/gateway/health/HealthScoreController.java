package com.sentinelgateway.gateway.health;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint for the gateway composite health score (Phase 39).
 */
@RestController
@RequestMapping("/admin/health-score")
@PreAuthorize("hasRole('ADMIN')")
public class HealthScoreController {

    private final HealthScoreService service;

    public HealthScoreController(HealthScoreService service) {
        this.service = service;
    }

    @GetMapping
    public HealthScoreResult getHealthScore() {
        return service.compute();
    }
}
