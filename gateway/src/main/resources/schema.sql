CREATE TABLE IF NOT EXISTS api_keys (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id   VARCHAR(128)  NOT NULL,
    tenant_id   VARCHAR(128),
    key_hash    VARCHAR(64)   NOT NULL UNIQUE,
    status      VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    scopes      VARCHAR(512),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMP,
    revoked_at  TIMESTAMP
);
