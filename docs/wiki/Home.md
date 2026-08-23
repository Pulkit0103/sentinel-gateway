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

## Project Status

All **25 development phases** are complete. The project is production-ready for demo and portfolio purposes.

> **Note:** Threat detection is demonstrable WAF-style detection — not a replacement for a commercial WAF product.
