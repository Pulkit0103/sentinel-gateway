# Sentinel Gateway

[![Build](https://github.com/Pulkit0103/sentinel-gateway/actions/workflows/ci.yml/badge.svg)](https://github.com/Pulkit0103/sentinel-gateway/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**A Zero-Trust API Security and Traffic Management Platform**

Sentinel Gateway is a production-grade API gateway built on Spring Boot and Spring Cloud Gateway. It enforces a zero-trust security model: every request is authenticated, authorized, tenant-isolated, rate-limited, and audited before it reaches a downstream service.

---

## Architecture Overview

```
                         INTERNET
                            │
                            ▼
                     Load Balancer
                            │
                            ▼
                 ┌─────────────────────┐
                 │   Sentinel Gateway  │
                 │                     │
                 │ ○ TLS               │
                 │ ○ Authentication    │
                 │ ○ Authorization     │
                 │ ○ Tenant Isolation  │
                 │ ○ Threat Detection  │
                 │ ○ Rate Limiting     │
                 │ ○ Quotas            │
                 │ ○ Request Signing   │
                 │ ○ Policy Engine     │
                 │ ○ Dynamic Routing   │
                 └──────────┬──────────┘
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
        User Service   Order Service  Payment Service
```

---

## Technology Stack

| Layer          | Technology                                              |
|----------------|---------------------------------------------------------|
| Language       | Java 17 (target: Java 21 when available)                |
| Framework      | Spring Boot 3.3, Spring Cloud Gateway 4.1               |
| Security       | Spring Security, OAuth2/OIDC, JWT (phases 3–6)          |
| Identity       | Keycloak (phase 3+)                                     |
| Cache / State  | Redis (phase 8+)                                        |
| Messaging      | Kafka (phase 13+)                                       |
| Database       | PostgreSQL + Spring Data JPA (phase 6+)                 |
| Observability  | OpenTelemetry, Prometheus, Grafana (phase 14+)          |
| Testing        | JUnit 5, Mockito, Spring Boot Test, Testcontainers      |
| Containers     | Docker, Docker Compose, Kubernetes, Helm                |
| CI/CD          | GitHub Actions                                          |
| Admin UI       | Next.js + TypeScript (phase 18+)                        |

---

## Current Status: Phase 1 — Bootstrap

The current milestone establishes the foundational project structure:

- Spring Cloud Gateway routing to a hello-service
- Health and readiness endpoints via Spring Boot Actuator
- Docker support (Dockerfiles + Docker Compose)
- Full Maven multi-module build

**Request flow (Phase 1):**
```
Client → :8080 (Gateway) → :8081 (Hello Service)
```

---

## Quick Start

### Prerequisites

| Tool    | Required Version |
|---------|-----------------|
| Java    | 17+ (21 recommended) |
| Maven   | 3.8+            |
| Docker  | 24+ (for Docker Compose setup) |
| Git     | 2.40+           |

### Build and run locally (without Docker)

```bash
# 1. Clone the repository
git clone https://github.com/Pulkit0103/sentinel-gateway.git
cd sentinel-gateway

# 2. Build all modules
mvn clean package -DskipTests

# 3. Start the hello-service (terminal 1)
java -jar test-services/hello-service/target/hello-service-*.jar

# 4. Start the gateway (terminal 2)
java -jar gateway/target/sentinel-gateway-*.jar

# 5. Test the route
curl http://localhost:8080/api/hello
```

### Build and run with Docker Compose

```bash
# Build and start all services
docker compose -f infrastructure/docker-compose/docker-compose.yml up --build

# Test
curl http://localhost:8080/api/hello

# Stop
docker compose -f infrastructure/docker-compose/docker-compose.yml down
```

---

## API Reference

### Phase 1 — Available Endpoints

| Method | Path               | Service       | Description             |
|--------|--------------------|---------------|-------------------------|
| GET    | `/api/hello`       | hello-service | Health probe / smoke test |
| GET    | `/actuator/health` | gateway       | Gateway health status   |
| GET    | `/actuator/info`   | gateway       | Gateway info            |

---

## Testing

```bash
# Run all tests
mvn test

# Run gateway tests only
mvn test -pl gateway

# Run hello-service tests only
mvn test -pl test-services/hello-service
```

---

## Environment Configuration

Copy `.env.example` to `.env` and fill in values for local development.  
**Never commit `.env` or any file containing real credentials.**

---

## Roadmap

See [ROADMAP.md](ROADMAP.md) for the full 25-phase development plan.

---

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for component design and data-flow diagrams.

---

## Security Model

See [SECURITY_MODEL.md](SECURITY_MODEL.md) for the zero-trust security design.

---

## License

MIT License — see [LICENSE](LICENSE) for details.
