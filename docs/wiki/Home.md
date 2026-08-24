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

### Production Hardening — Complete (v0.4.0-SNAPSHOT)

**66 phases complete** — all production-hardening observability, traffic analysis, and security enforcement filters are implemented, tested, and merged to `main`.

| Category | Phases | Status |
|----------|--------|--------|
| Core Security Pipeline (JWT/OIDC, RBAC, Rate Limiting, Threat Detection) | 1–25 | ✅ Complete |
| Dynamic Admin CRUD + Route Persistence | 26–30 | ✅ Complete |
| Advanced Analytics — Slow Requests, Clock Skew, Error Paths, Cache TTL | 31–50 | ✅ Complete |
| Traffic Analysis — Method Stats, Hop Count, Path Length, Response Size, Concurrency, Status Codes | 51–59 | ✅ Complete |
| Extended Analytics — Query Params, Scheme, Path Depth, Referer, Route Error Rate, Throughput | 60–66 | ✅ Complete |

### Feature Highlights (v0.4.x)

| Feature | Admin Endpoint |
|---------|---------------|
| Request throughput (RPS) sliding windows (1s/10s/60s) + peak | `GET /admin/throughput-stats` |
| Per-route error rate (1-min/5-min/15-min windows) | `GET /admin/route-error-rate` |
| Referer domain distribution (top-N) | `GET /admin/referer-stats` |
| URL path-depth distribution | `GET /admin/path-depth-stats` |
| URI scheme distribution (HTTP vs HTTPS) | `GET /admin/scheme-stats` |
| Query parameter count distribution | `GET /admin/query-param-stats` |
| Response size distribution + sample ring-buffer | `GET /admin/response-size-stats` |
| In-flight concurrency tracking + peak | `GET /admin/concurrent-request-stats` |
| HTTP status code distribution (per-code + 2xx/3xx/4xx/5xx) | `GET /admin/status-code-stats` |
| Per-route latency percentiles (p50/p95/p99) | `GET /admin/latency-stats` |
| Live dashboard (all metrics in one response) | `GET /admin/dashboard` |

> **Note:** Threat detection is demonstrable WAF-style detection — not a replacement for a commercial WAF product.

### Older Phases — Complete

All **25 original development phases** are complete, providing a production-grade
zero-trust gateway with JWT/OIDC auth, RBAC, rate limiting, threat detection,
audit logging, resilience, and full CI/CD pipeline.
