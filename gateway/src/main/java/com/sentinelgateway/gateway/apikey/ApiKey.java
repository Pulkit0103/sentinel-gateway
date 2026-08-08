package com.sentinelgateway.gateway.apikey;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Table("api_keys")
public class ApiKey {

    @Id
    private Long id;

    @Column("client_id")
    private String clientId;

    @Column("tenant_id")
    private String tenantId;

    @Column("key_hash")
    private String keyHash;

    @Column("status")
    private String status;

    @Column("scopes")
    private String scopes;

    @Column("created_at")
    private Instant createdAt;

    @Column("expires_at")
    private Instant expiresAt;

    @Column("revoked_at")
    private Instant revokedAt;

    public ApiKey() {}

    public ApiKey(String clientId, String tenantId, String keyHash, String status,
                  String scopes, Instant createdAt, Instant expiresAt) {
        this.clientId = clientId;
        this.tenantId = tenantId;
        this.keyHash = keyHash;
        this.status = status;
        this.scopes = scopes;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        if (!ApiKeyStatus.ACTIVE.name().equals(status)) return false;
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) return false;
        return true;
    }

    public List<String> scopeList() {
        if (scopes == null || scopes.isBlank()) return List.of();
        return Arrays.asList(scopes.split("\\s+"));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
