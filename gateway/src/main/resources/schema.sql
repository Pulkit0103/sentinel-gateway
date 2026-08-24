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

-- Phase 2: Persistent route definitions managed via Admin API
-- Phase 4: Added strip_prefix and add_request_headers columns
-- Phase 7: Added allowed_ips and blocked_ips columns for per-route IP filtering
-- Phase 8: Added max_body_bytes column for per-route request size limiting
-- Phase 9: Added timeout_ms column for per-route response timeout
-- Phase 11: Added cache_ttl_seconds column for per-route response caching
CREATE TABLE IF NOT EXISTS routes (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id             VARCHAR(128)  NOT NULL UNIQUE,
    path                 VARCHAR(256)  NOT NULL,
    service_uri          VARCHAR(512)  NOT NULL,
    methods              VARCHAR(128),
    enabled              BOOLEAN       NOT NULL DEFAULT TRUE,
    required_scopes      VARCHAR(512),
    tenant_required      BOOLEAN       NOT NULL DEFAULT FALSE,
    rate_limit_policy    VARCHAR(64)   NOT NULL DEFAULT 'DEFAULT',
    strip_prefix         SMALLINT      NOT NULL DEFAULT 0,
    add_request_headers  VARCHAR(1024),
    allowed_ips          VARCHAR(1024),
    blocked_ips          VARCHAR(1024),
    max_body_bytes       BIGINT,
    timeout_ms           BIGINT,
    cache_ttl_seconds    INT,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
