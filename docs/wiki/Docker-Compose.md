# Docker Compose

The full local stack is defined in `infrastructure/docker-compose/docker-compose.yml`. It brings up all infrastructure dependencies, the gateway, all test downstream services, and the admin dashboard in the correct startup order.

## Services

| Service | Container | Port | Image |
|---|---|---|---|
| PostgreSQL | `sentinel-postgres` | 5432 | `postgres:16-alpine` |
| Redis | `sentinel-redis` | 6379 | `redis:7-alpine` |
| Kafka | `sentinel-kafka` | 9092 | `bitnami/kafka:3.7` |
| Keycloak | `keycloak` | 8180 | `quay.io/keycloak/keycloak:25.0` |
| Prometheus | `sentinel-prometheus` | 9090 | `prom/prometheus:v2.54.1` |
| Grafana | `sentinel-grafana` | 3000 | `grafana/grafana:11.2.0` |
| Sentinel Gateway | `sentinel-gateway` | 8080 | Built from `gateway/Dockerfile` |
| Hello Service | `hello-service` | 8081 | Built from `test-services/hello-service/Dockerfile` |
| User Service | `user-service` | 8082 | Built from `test-services/user-service/Dockerfile` |
| Order Service | `order-service` | 8083 | Built from `test-services/order-service/Dockerfile` |
| Payment Service | `payment-service` | 8084 | Built from `test-services/payment-service/Dockerfile` |
| Admin Dashboard | `sentinel-dashboard` | 3001 | Built from `admin-dashboard/Dockerfile` |

## Startup Order

The `depends_on` conditions guarantee:

```
PostgreSQL (healthy) ┐
Redis      (healthy) ├─→ Sentinel Gateway (healthy) → Admin Dashboard
Keycloak   (healthy) ┘
Kafka      (healthy) ┘

Hello/User/Order/Payment services start independently
```

Each service has a `healthcheck` so Docker Compose waits for genuine readiness, not just container start.

## Quick Start

```bash
cd infrastructure/docker-compose

# Copy and optionally edit environment config
cp .env.example .env

# Build all images and start (first run is slow — Maven + npm downloads)
docker compose up --build

# Or start in detached mode
docker compose up -d --build
```

## Useful Commands

```bash
# Follow gateway logs
docker compose logs -f sentinel-gateway

# Follow all logs
docker compose logs -f

# Check status
docker compose ps

# Restart a single service
docker compose restart sentinel-gateway

# Rebuild a single image
docker compose up --build sentinel-gateway

# Stop (keep volumes / data)
docker compose down

# Stop and wipe all data volumes (clean slate)
docker compose down -v

# Execute into a running container
docker compose exec sentinel-gateway sh
```

## Health Checks

All services expose health endpoints:

| Service | Health URL |
|---|---|
| Gateway | http://localhost:8080/actuator/health |
| Gateway liveness | http://localhost:8080/actuator/health/liveness |
| Gateway readiness | http://localhost:8080/actuator/health/readiness |
| Hello Service | http://localhost:8081/actuator/health |
| User Service | http://localhost:8082/actuator/health |
| Order Service | http://localhost:8083/actuator/health |
| Payment Service | http://localhost:8084/actuator/health |

## Keycloak Pre-configured Realm

The `infrastructure/keycloak/realm-export.json` is automatically imported on Keycloak startup. It includes:

- Realm: `sentinel`
- Client: `sentinel-gateway-client`
- Test users:
  - `alice` / `alice-password` — USER role
  - `bob` / `bob-password` — ADMIN role

## Grafana Dashboards

Grafana is pre-provisioned with:
- **Prometheus datasource** — auto-configured on startup
- **Gateway dashboard** — shows request rate, error rate, latency histograms, rate limit rejections, threat blocks

Login: http://localhost:3000 — `admin` / `admin`

## Environment Variables

See [Configuration Reference](Configuration-Reference) for all available variables. Defaults in `.env.example` work for local development out of the box.

Key overrides for production-like testing:

```env
# Enable full security stack
SENTINEL_RATE_LIMIT_ENABLED=true
SENTINEL_QUOTA_ENABLED=true
SENTINEL_THREAT_DETECTION_ENABLED=true
SENTINEL_AUDIT_KAFKA_ENABLED=true

# Use strong secrets
DATABASE_PASSWORD=my-strong-db-password
REDIS_PASSWORD=my-strong-redis-password
HMAC_SHARED_SECRET=my-strong-hmac-secret
```
