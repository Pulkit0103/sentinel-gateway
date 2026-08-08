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

CREATE TABLE IF NOT EXISTS security_policies (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_id                VARCHAR(128)  NOT NULL UNIQUE,
    route_id                 VARCHAR(128)  NOT NULL UNIQUE,
    require_mfa              BOOLEAN       NOT NULL DEFAULT FALSE,
    request_signing_required BOOLEAN       NOT NULL DEFAULT FALSE,
    required_scopes          VARCHAR(512),
    allowed_methods          VARCHAR(256),
    rate_limit_policy        VARCHAR(64),
    created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
