# Configuration Reference

All configuration is injected via environment variables. Copy `infrastructure/docker-compose/.env.example` to `.env` and set values before starting the stack.

**Never commit `.env`** — only `.env.example` is tracked.

## Gateway Core

| Variable | Default | Description |
|---|---|---|
| `GATEWAY_PORT` | `8080` | Port the gateway listens on |

## JWT / Keycloak

| Variable | Default | Description |
|---|---|---|
| `KEYCLOAK_JWKS_URI` | `http://localhost:8180/realms/sentinel/protocol/openid-connect/certs` | JWKS endpoint for JWT signature validation |
| `KEYCLOAK_ISSUER` | `http://localhost:8180/realms/sentinel` | Expected JWT `iss` claim value |
| `KEYCLOAK_PORT` | `8180` | Keycloak host port (Docker Compose) |
| `KEYCLOAK_ADMIN_USER` | `admin` | Keycloak admin username |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin` | Keycloak admin password |

## Database (PostgreSQL / R2DBC)

| Variable | Default | Description |
|---|---|---|
| `R2DBC_URL` | `r2dbc:h2:mem:///sentinel` | R2DBC connection URL. Use `r2dbc:postgresql://...` in production |
| `DATABASE_PASSWORD` | `sentinel_secret` | PostgreSQL password |
| `POSTGRES_PORT` | `5432` | PostgreSQL host port (Docker Compose) |
| `SPRING_SQL_INIT_MODE` | `always` | `always` = run schema.sql on startup; `never` = skip |

## Redis

| Variable | Default | Description |
|---|---|---|
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `SPRING_DATA_REDIS_PASSWORD` | — | Redis AUTH password |

## Rate Limiting

| Variable | Default | Description |
|---|---|---|
| `SENTINEL_RATE_LIMIT_ENABLED` | `false` | Enable Redis-backed rate limiting. Set `true` when Redis is available |

Rate limit tiers are configured in `application.yml` under `sentinel.rate-limit.policies`.

## Quotas

| Variable | Default | Description |
|---|---|---|
| `SENTINEL_QUOTA_ENABLED` | `false` | Enable tenant quota enforcement. Requires Redis |

## Threat Detection

| Variable | Default | Description |
|---|---|---|
| `SENTINEL_THREAT_DETECTION_ENABLED` | `false` | Enable WAF-style pattern detection |

Thresholds are configured in `application.yml` under `sentinel.threat-detection`.

## Kafka (Audit)

| Variable | Default | Description |
|---|---|---|
| `SENTINEL_AUDIT_KAFKA_ENABLED` | `false` | Publish audit events to Kafka. Set `true` in full-stack environments |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `KAFKA_PORT` | `9092` | Kafka host port (Docker Compose) |

## HMAC Request Signing

| Variable | Default | Description |
|---|---|---|
| `HMAC_SHARED_SECRET` | `change-me-in-production` | Shared secret for HMAC-SHA256 signing. **Always override in production** |

Request signing is opt-in per route via `sentinel.policy.policies[].request-signing-required`.

## Downstream Services

| Variable | Default | Description |
|---|---|---|
| `HELLO_SERVICE_URL` | `http://localhost:8081` | hello-service base URL |
| `USER_SERVICE_URL` | `http://localhost:8082` | user-service base URL |
| `ORDER_SERVICE_URL` | `http://localhost:8083` | order-service base URL |
| `PAYMENT_SERVICE_URL` | `http://localhost:8084` | payment-service base URL |

## Observability

| Variable | Default | Description |
|---|---|---|
| `PROMETHEUS_PORT` | `9090` | Prometheus host port (Docker Compose) |
| `GRAFANA_PORT` | `3000` | Grafana host port (Docker Compose) |
| `GRAFANA_USER` | `admin` | Grafana admin user |
| `GRAFANA_PASSWORD` | `admin` | Grafana admin password |

## Admin Dashboard

| Variable | Default | Description |
|---|---|---|
| `DASHBOARD_PORT` | `3001` | Admin dashboard host port (Docker Compose) |
| `NEXT_PUBLIC_GATEWAY_URL` | `http://localhost:8080` | Gateway URL the browser uses to call the admin API |

## application.yml Overrides

Some values can only be set in `gateway/src/main/resources/application.yml`:

- `sentinel.threat-detection.log-threshold` / `block-threshold` / `alert-threshold` — risk score thresholds
- `sentinel.rate-limit.policies.*` — rate limit tier definitions
- `sentinel.policy.policies.*` — per-route security policies
- `sentinel.gateway.routes.*` — route definitions
- `resilience4j.circuitbreaker.configs.default.*` — circuit breaker tuning
