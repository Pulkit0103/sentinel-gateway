# Sentinel Gateway — Performance Benchmarks

k6 benchmark suite for measuring gateway latency and throughput.

## Prerequisites

- [k6](https://k6.io/docs/getting-started/installation/) installed
- Full stack running: `cd infrastructure/docker-compose && docker compose up -d`
- Wait for all services healthy: `docker compose ps`

## Quick Start

```bash
# Routing only (no auth) — theoretical minimum latency
k6 run performance/k6/01-routing-only.js

# JWT auth — with full Spring Security filter chain
export TOKEN=$(bash performance/k6/scripts/get-token.sh)
k6 run -e TOKEN=$TOKEN performance/k6/02-jwt-auth.js

# API key auth — with R2DBC PostgreSQL lookup
export API_KEY=sgk_your_key_here
k6 run -e API_KEY=$API_KEY performance/k6/03-api-key-auth.js

# Rate limiting — includes Redis INCR/EXPIRE
k6 run performance/k6/04-rate-limit.js

# Full production pipeline — all filters active
k6 run -e TOKEN=$TOKEN performance/k6/05-full-pipeline.js
```

## Environment Variables

| Variable            | Default                  | Description                        |
|---------------------|--------------------------|------------------------------------|
| `GATEWAY_URL`       | `http://localhost:8080`  | Gateway base URL                   |
| `TOKEN`             | (empty)                  | JWT access token for auth tests    |
| `API_KEY`           | (empty)                  | API key (`sgk_...`) for key tests  |
| `KEYCLOAK_URL`      | `http://localhost:8180`  | Used by get-token.sh               |
| `KEYCLOAK_USERNAME` | `alice`                  | Test user for token acquisition    |
| `KEYCLOAK_PASSWORD` | `alice-password`         | Test user password                 |

## Baseline Targets

| Scenario          | P50    | P95     | P99     | Error Rate |
|-------------------|--------|---------|---------|------------|
| Routing only      | < 5ms  | < 20ms  | < 50ms  | < 1%       |
| JWT auth          | < 10ms | < 50ms  | < 100ms | < 1%       |
| API key auth      | < 15ms | < 60ms  | < 150ms | < 1%       |
| Rate limiting     | —      | < 100ms | —       | < 5%       |
| Full pipeline     | < 20ms | < 100ms | < 200ms | < 1%       |

## Load Profile

All scenarios (except rate-limit) use the same 8-minute staged profile:
```
30s  ramp to 50 VUs   (~1K req/s)
2m   hold 50 VUs
30s  ramp to 250 VUs  (~5K req/s)
2m   hold 250 VUs
30s  ramp to 500 VUs  (~10K req/s)
2m   hold 500 VUs
30s  cool down
```

## Output

k6 outputs per-request metrics to stdout. For persistent storage:
```bash
# JSON output for later analysis
k6 run --out json=results.json performance/k6/05-full-pipeline.js

# InfluxDB + Grafana (requires InfluxDB running)
k6 run --out influxdb=http://localhost:8086/k6 performance/k6/05-full-pipeline.js
```
