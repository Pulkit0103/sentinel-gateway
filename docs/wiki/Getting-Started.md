# Getting Started

## Prerequisites

| Tool | Minimum Version | Notes |
|---|---|---|
| Java | 17 | 21 also works |
| Maven | 3.9 | |
| Docker | 24 | For Docker Compose setup |
| Docker Compose | v2 | Bundled with Docker Desktop |
| Node.js | 18 | For admin dashboard development only |

## Option A — Minimal Local Run (no Docker)

The gateway starts cleanly without Redis, Kafka, or Keycloak. Rate limiting, quotas, and JWT auth are disabled by default and fail-open.

```bash
# 1. Clone
git clone https://github.com/Pulkit0103/sentinel-gateway.git
cd sentinel-gateway

# 2. Build all modules
mvn clean package -DskipTests

# 3. Start hello-service (terminal 1)
java -jar test-services/hello-service/target/hello-service-*.jar

# 4. Start the gateway (terminal 2)
java -jar gateway/target/sentinel-gateway-*.jar

# 5. Test the smoke route
curl http://localhost:8080/api/hello
```

Expected response:
```json
{"message":"Hello from Sentinel Gateway!","service":"hello-service","timestamp":"2024-..."}
```

```bash
# 6. Check gateway health
curl http://localhost:8080/actuator/health
```

```json
{"status":"UP","components":{...}}
```

## Option B — Full Stack with Docker Compose

```bash
cd infrastructure/docker-compose

# Copy environment config
cp .env.example .env
# Edit .env to set passwords if needed (defaults work for local dev)

# Build and start all services
docker compose up --build

# Or start in background
docker compose up -d --build

# Follow gateway logs
docker compose logs -f sentinel-gateway
```

### Service URLs

| Service | URL | Default Credentials |
|---|---|---|
| Gateway | http://localhost:8080 | — |
| Admin Dashboard | http://localhost:3001 | Bearer token from Keycloak |
| Keycloak Admin | http://localhost:8180 | admin / admin |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | admin / admin |

### Get a token and make an authenticated request

```bash
# Get JWT from Keycloak
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/sentinel/protocol/openid-connect/token \
  -d 'client_id=sentinel-gateway-client' \
  -d 'grant_type=password' \
  -d 'username=alice' \
  -d 'password=alice-password' \
  | jq -r .access_token)

# Call the gateway with authentication
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/users
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/orders
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/routes
```

### Stop the stack

```bash
docker compose down          # stop, keep volumes
docker compose down -v       # stop and remove all data volumes
```

## Running Tests

```bash
# All tests
mvn test

# Gateway tests only
mvn test -pl gateway

# Skip tests during build
mvn package -DskipTests
```
