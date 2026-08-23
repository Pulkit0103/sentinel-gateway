# Sentinel Gateway — Wiki

Welcome to the Sentinel Gateway project wiki. Use the sidebar to navigate between topics.

## Overview

Sentinel Gateway is a production-grade **zero-trust API security and traffic management platform** built on Spring Boot 3.3 and Spring Cloud Gateway.

Every request passes through a nine-layer security pipeline before it reaches a protected downstream service:

```
Client → TLS → Auth → AuthZ → Tenant → Policy → Threat → RateLimit → Quota → Route
```

## Wiki Pages

| Page | Description |
|---|---|
| [Getting Started](Getting-Started) | Installation, prerequisites, first run |
| [Architecture](Architecture) | Component design, filter chain, infrastructure diagram |
| [Security Pipeline](Security-Pipeline) | Detailed walkthrough of every security layer |
| [Authentication](Authentication) | JWT/OIDC and API key authentication |
| [Authorization & RBAC](Authorization) | Role-based access control and permissions |
| [Rate Limiting & Quotas](Rate-Limiting-and-Quotas) | Redis-backed token buckets and tenant quotas |
| [Threat Detection](Threat-Detection) | WAF patterns and risk scoring |
| [Admin API](Admin-API) | REST API for managing routes, keys, and policies |
| [Admin Dashboard](Admin-Dashboard) | Next.js management UI guide |
| [Docker Compose](Docker-Compose) | Running the full local stack |
| [Kubernetes & Helm](Kubernetes-and-Helm) | Production deployment |
| [Configuration Reference](Configuration-Reference) | All environment variables |
| [CI/CD](CICD) | GitHub Actions pipeline |
| [Development Guide](Development-Guide) | Contributing, testing, project structure |
| [CHANGELOG](CHANGELOG) | Version history and feature additions |

## Project Status

### Phase 2 — Active (v0.2.0-SNAPSHOT)

**Dynamic Admin CRUD + Route Persistence** — routes and policies are now fully
manageable at runtime via REST API with no gateway restart required.

| Feature | Status |
|---------|--------|
| Route CRUD (create/update/delete/enable/disable) | ✅ v0.2.0 |
| Route persistence (PostgreSQL via R2DBC) | ✅ v0.2.0 |
| Policy CRUD (create/update/delete) | ✅ v0.2.0 |
| IP Blocklist management API | ✅ v0.2.0 |
| Hot-reload via `RefreshRoutesEvent` | ✅ v0.2.0 |

### Phase 1 — Complete (v0.1.0)

All **25 original development phases** are complete, providing a production-grade
zero-trust gateway with JWT/OIDC auth, RBAC, rate limiting, threat detection,
audit logging, resilience, and full CI/CD pipeline.

> **Note:** Threat detection is demonstrable WAF-style detection — not a replacement for a commercial WAF product.
