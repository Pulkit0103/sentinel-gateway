package com.sentinelgateway.gateway.apikey;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Manages API key lifecycle.
 *
 * Raw key format: sgk_<base64url(32 random bytes)>
 * Storage: SHA-256 hex digest of the raw key — never the raw key itself.
 * The raw key is shown exactly once at creation time and never stored.
 */
@Service
public class ApiKeyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "sgk_";

    private final ApiKeyRepository repository;

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    /**
     * Generates a new API key, persists the hash, and returns the raw key.
     * The returned raw key is the only opportunity to see it — it is not stored.
     */
    public Mono<CreatedApiKey> create(String clientId, String tenantId, String scopes, Instant expiresAt) {
        String rawKey = generateRawKey();
        String hash = sha256Hex(rawKey);
        ApiKey entity = new ApiKey(clientId, tenantId, hash, ApiKeyStatus.ACTIVE.name(),
                scopes, Instant.now(), expiresAt);
        return repository.save(entity)
                .map(saved -> new CreatedApiKey(rawKey, saved));
    }

    /**
     * Validates the raw key and returns the ApiKey if it is active and not expired.
     * Returns empty if the key is unknown, revoked, or expired.
     */
    public Mono<ApiKey> validate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) return Mono.empty();
        String hash = sha256Hex(rawKey);
        return repository.findByKeyHash(hash)
                .filter(ApiKey::isActive);
    }

    /**
     * Marks the key as REVOKED. Idempotent — revoking an already-revoked key is a no-op.
     */
    public Mono<ApiKey> revoke(Long keyId) {
        return repository.findById(keyId)
                .flatMap(key -> {
                    key.setStatus(ApiKeyStatus.REVOKED.name());
                    key.setRevokedAt(Instant.now());
                    return repository.save(key);
                });
    }

    static String generateRawKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record CreatedApiKey(String rawKey, ApiKey entity) {}
}
