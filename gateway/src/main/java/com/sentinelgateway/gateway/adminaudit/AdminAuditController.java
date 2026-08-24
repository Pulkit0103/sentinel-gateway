package com.sentinelgateway.gateway.adminaudit;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Admin endpoint for browsing the in-memory admin operation audit trail (Phase 31).
 *
 * GET  /admin/audit-log         — recent admin operations (oldest → newest)
 * GET  /admin/audit-log?limit=N — last N records
 * POST /admin/audit-log/clear   — wipe the in-memory log
 *
 * Requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin/audit-log")
public class AdminAuditController {

    private final AdminAuditRegistry registry;

    public AdminAuditController(AdminAuditRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public Mono<Map<String, Object>> getLog(
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        List<AdminAuditRecord> all = registry.all();
        List<AdminAuditRecord> page = limit > 0 && limit < all.size()
                ? all.subList(all.size() - limit, all.size())
                : all;
        return Mono.just(Map.of(
                "total", registry.size(),
                "returned", page.size(),
                "records", page
        ));
    }

    @PostMapping("/clear")
    public Mono<Map<String, Object>> clearLog() {
        int before = registry.size();
        registry.clear();
        return Mono.just(Map.of("cleared", before, "remaining", 0));
    }
}
