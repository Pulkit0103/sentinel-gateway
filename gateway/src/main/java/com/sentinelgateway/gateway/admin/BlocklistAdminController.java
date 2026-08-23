package com.sentinelgateway.gateway.admin;

import com.sentinelgateway.gateway.threat.BlocklistService;
import com.sentinelgateway.gateway.threat.ThreatDetectionProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;

/**
 * Admin REST API for IP blocklist management.
 *
 * GET  /admin/blocklist          — list all blocked IPs
 * POST /admin/blocklist          — add an IP (persists to Redis if available)
 * DELETE /admin/blocklist/{ip}   — remove an IP (Redis-managed only)
 *
 * Works with or without the threat detection filter active. The filter reads
 * from ThreatDetectionProperties which this API keeps in sync.
 *
 * Secured by ROLE_ADMIN (configured in SecurityConfig).
 */
@RestController
@RequestMapping("/admin/blocklist")
public class BlocklistAdminController {

    private final ThreatDetectionProperties properties;
    private final Optional<BlocklistService> blocklistService;

    public BlocklistAdminController(ThreatDetectionProperties properties,
                                    @Autowired(required = false) BlocklistService blocklistService) {
        this.properties = properties;
        this.blocklistService = Optional.ofNullable(blocklistService);
    }

    /** List all currently blocked IPs (YAML-static + Redis-persisted). */
    @GetMapping
    public Flux<String> listBlockedIps() {
        if (blocklistService.isPresent()) {
            return blocklistService.get().listBlockedIps();
        }
        return Flux.fromIterable(Set.copyOf(properties.getBlockedIps()));
    }

    /** Add an IP to the blocklist. Effective immediately for new requests. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<BlocklistResponse> blockIp(@Valid @RequestBody BlockIpRequest request) {
        String ip = request.ip();
        if (blocklistService.isPresent()) {
            return blocklistService.get().blockIp(ip)
                    .thenReturn(new BlocklistResponse(ip, "blocked"));
        }
        if (!properties.getBlockedIps().contains(ip)) {
            properties.getBlockedIps().add(ip);
        }
        return Mono.just(new BlocklistResponse(ip, "blocked (in-memory only — Redis not available)"));
    }

    /** Remove an IP from the blocklist. Static YAML entries cannot be removed at runtime. */
    @DeleteMapping("/{ip}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> unblockIp(@PathVariable String ip) {
        if (blocklistService.isPresent()) {
            return blocklistService.get().unblockIp(ip)
                    .flatMap(removed -> {
                        if (!removed) {
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "IP not in blocklist: " + ip));
                        }
                        return Mono.<Void>empty();
                    });
        }
        boolean removed = properties.getBlockedIps().remove(ip);
        if (!removed) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "IP not in blocklist: " + ip));
        }
        return Mono.empty();
    }

    record BlockIpRequest(
            @NotBlank
            @Pattern(regexp = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$",
                     message = "must be a valid IPv4 address")
            String ip
    ) {}

    record BlocklistResponse(String ip, String status) {}
}
