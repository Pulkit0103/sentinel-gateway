-- Sentinel Gateway schema — PostgreSQL dialect
-- Executed once by postgres:16 on first container start via docker-entrypoint-initdb.d.
-- Spring's schema.sql (H2 dialect) is disabled in Docker Compose via SPRING_SQL_INIT_MODE=never.

CREATE TABLE IF NOT EXISTS api_keys (
    id          BIGSERIAL           PRIMARY KEY,
    client_id   VARCHAR(128)        NOT NULL,
    tenant_id   VARCHAR(128),
    key_hash    VARCHAR(64)         NOT NULL UNIQUE,
    status      VARCHAR(16)         NOT NULL DEFAULT 'ACTIVE',
    scopes      VARCHAR(512),
    created_at  TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS security_policies (
    id                       BIGSERIAL       PRIMARY KEY,
    policy_id                VARCHAR(128)    NOT NULL UNIQUE,
    route_id                 VARCHAR(128)    NOT NULL UNIQUE,
    require_mfa              BOOLEAN         NOT NULL DEFAULT FALSE,
    request_signing_required BOOLEAN         NOT NULL DEFAULT FALSE,
    required_scopes          VARCHAR(512),
    allowed_methods          VARCHAR(256),
    rate_limit_policy        VARCHAR(64),
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
