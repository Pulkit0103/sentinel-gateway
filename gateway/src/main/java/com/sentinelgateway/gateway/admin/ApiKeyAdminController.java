package com.sentinelgateway.gateway.admin;

import com.sentinelgateway.gateway.apikey.ApiKey;
import com.sentinelgateway.gateway.apikey.ApiKeyRepository;
import com.sentinelgateway.gateway.apikey.ApiKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/admin/api-keys")
public class ApiKeyAdminController {

    private final ApiKeyRepository repository;
    private final ApiKeyService service;

    public ApiKeyAdminController(ApiKeyRepository repository, ApiKeyService service) {
        this.repository = repository;
        this.service = service;
    }

    /** List all API keys — keyHash is intentionally excluded from the response. */
    @GetMapping
    public Flux<ApiKeyResponse> listKeys() {
        return repository.findAll().map(ApiKeyResponse::from);
    }

    /** Create a new API key. The rawKey is returned exactly once — it is never stored. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CreatedApiKeyResponse> createKey(@RequestBody CreateKeyRequest request) {
        return service.create(request.clientId(), request.tenantId(), request.scopes(), request.expiresAt())
                .map(c -> new CreatedApiKeyResponse(
                        c.rawKey(),
                        c.entity().getId(),
                        c.entity().getClientId(),
                        c.entity().getTenantId(),
                        c.entity().getStatus(),
                        c.entity().getScopes(),
                        c.entity().getCreatedAt(),
                        c.entity().getExpiresAt()
                ));
    }

    /** Revoke an API key by ID. Idempotent — revoking a non-existent key returns 204. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> revokeKey(@PathVariable("id") Long id) {
        return service.revoke(id).then();
    }

    record CreateKeyRequest(String clientId, String tenantId, String scopes, Instant expiresAt) {}

    record CreatedApiKeyResponse(String rawKey, Long id, String clientId, String tenantId,
                                 String status, String scopes, Instant createdAt, Instant expiresAt) {}

    record ApiKeyResponse(Long id, String clientId, String tenantId, String status,
                          String scopes, Instant createdAt, Instant expiresAt, Instant revokedAt) {
        static ApiKeyResponse from(ApiKey k) {
            return new ApiKeyResponse(k.getId(), k.getClientId(), k.getTenantId(), k.getStatus(),
                    k.getScopes(), k.getCreatedAt(), k.getExpiresAt(), k.getRevokedAt());
        }
    }
}
