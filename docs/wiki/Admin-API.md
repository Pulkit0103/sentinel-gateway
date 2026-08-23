# Admin API

The gateway exposes a REST admin API for managing routes, API keys, security policies, and the IP blocklist at runtime — **no restart required**.

All admin endpoints require an authenticated request with the `ADMIN` role.

## Base URL

```
http://localhost:8080/admin
```

## Authentication

Include a Bearer token from Keycloak in every request:

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/sentinel/protocol/openid-connect/token \
  -d 'client_id=sentinel-gateway-client&grant_type=password' \
  -d 'username=alice&password=alice-password' \
  | jq -r .access_token)
```

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/routes
```

---

## Routes API

Routes are persisted in the database and take effect immediately via `RefreshRoutesEvent`.

### List all routes

```
GET /admin/routes
```

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/routes
```

**Response:**
```json
[
  {
    "routeId": "user-service",
    "path": "/api/users/**",
    "serviceUri": "lb://user-service",
    "methods": ["GET", "POST", "PUT", "PATCH", "DELETE"],
    "enabled": true,
    "requiredScopes": [],
    "tenantRequired": false,
    "rateLimitPolicy": "DEFAULT"
  }
]
```

### Get a single route

```
GET /admin/routes/{routeId}
```

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/routes/user-service
```

### Create a route

```
POST /admin/routes
Content-Type: application/json
```

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "routeId": "my-service",
    "path": "/api/my/**",
    "serviceUri": "lb://my-service",
    "methods": ["GET", "POST"],
    "enabled": true,
    "requiredScopes": [],
    "tenantRequired": false,
    "rateLimitPolicy": "USER"
  }' \
  http://localhost:8080/admin/routes
```

Returns `201 Created` with the created route. Returns `409 Conflict` if `routeId` already exists.

### Update a route

```
PUT /admin/routes/{routeId}
Content-Type: application/json
```

```bash
curl -X PUT -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "path": "/api/my/v2/**",
    "serviceUri": "lb://my-service-v2",
    "methods": ["GET"],
    "enabled": true,
    "rateLimitPolicy": "PREMIUM"
  }' \
  http://localhost:8080/admin/routes/my-service
```

### Delete a route

```
DELETE /admin/routes/{routeId}
```

```bash
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/routes/my-service
```

Returns `204 No Content`. The route is removed permanently from the database and routing table.

### Enable / Disable a route

```
POST /admin/routes/{routeId}/enable
POST /admin/routes/{routeId}/disable
```

```bash
# Disable without deleting (requests get 404)
curl -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/routes/my-service/disable

# Re-enable
curl -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/routes/my-service/enable
```

---

## Policies API

### List all policies

```
GET /admin/policies
```

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/policies
```

**Response:**
```json
[
  {
    "id": 1,
    "policyId": "payment-service-policy",
    "routeId": "payment-service",
    "requireMfa": false,
    "requestSigningRequired": false,
    "requiredScopes": [],
    "allowedMethods": ["GET", "POST"],
    "rateLimitPolicy": "PREMIUM",
    "createdAt": "2025-01-01T00:00:00",
    "updatedAt": "2025-01-01T00:00:00"
  }
]
```

### Get policy by ID

```
GET /admin/policies/{policyId}
```

### Get policy for a route

```
GET /admin/policies/route/{routeId}
```

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/policies/route/payment-service
```

### Create a policy

```
POST /admin/policies
Content-Type: application/json
```

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "routeId": "my-service",
    "requireMfa": true,
    "requestSigningRequired": false,
    "requiredScopes": ["my:read"],
    "allowedMethods": ["GET"],
    "rateLimitPolicy": "USER"
  }' \
  http://localhost:8080/admin/policies
```

Returns `201 Created`. Returns `409 Conflict` if a policy already exists for the route.

### Update a policy

```
PUT /admin/policies/{policyId}
Content-Type: application/json
```

```bash
curl -X PUT -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "routeId": "my-service",
    "requireMfa": false,
    "requestSigningRequired": true,
    "rateLimitPolicy": "PREMIUM"
  }' \
  http://localhost:8080/admin/policies/my-policy-id
```

### Delete a policy

```
DELETE /admin/policies/{policyId}
```

```bash
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/policies/my-policy-id
```

Returns `204 No Content`. The route reverts to no policy enforcement.

---

## API Keys API

### List API keys

```
GET /admin/api-keys
```

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/api-keys
```

**Response** — `keyHash` is always excluded:
```json
[
  {
    "id": 1,
    "clientId": "mobile-app",
    "tenantId": "tenant-acme",
    "status": "ACTIVE",
    "scopes": "read write",
    "createdAt": "2025-01-01T00:00:00Z",
    "expiresAt": null
  }
]
```

### Create an API key

```
POST /admin/api-keys
Content-Type: application/json
```

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "my-service-client",
    "tenantId": "tenant-acme",
    "scopes": "user:read order:read",
    "expiresAt": "2026-01-01T00:00:00Z"
  }' \
  http://localhost:8080/admin/api-keys
```

**Response** — `rawKey` is shown **exactly once**:
```json
{
  "rawKey": "sgk_aBcDeFgHiJkLmNoPqRsTuVwXyZ01234567890abcdef",
  "id": 42,
  "clientId": "my-service-client",
  "tenantId": "tenant-acme",
  "status": "ACTIVE"
}
```

### Revoke an API key

```
DELETE /admin/api-keys/{id}
```

```bash
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/api-keys/42
```

Returns `204 No Content`. The key is immediately invalid for future requests.

---

## IP Blocklist API

Manage the runtime IP blocklist. Blocked IPs are persisted in Redis (`sentinel:blocklist`)
and take effect immediately for all new requests through the threat detection filter.

### List blocked IPs

```
GET /admin/blocklist
```

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/blocklist
```

**Response:**
```json
["192.168.1.100", "10.0.0.5"]
```

### Block an IP

```
POST /admin/blocklist
Content-Type: application/json
```

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"ip": "203.0.113.42"}' \
  http://localhost:8080/admin/blocklist
```

**Response:**
```json
{"ip": "203.0.113.42", "status": "blocked"}
```

The IP is persisted to Redis and added to the in-memory list. Subsequent requests
from this IP receive `403 Forbidden`.

### Unblock an IP

```
DELETE /admin/blocklist/{ip}
```

```bash
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/blocklist/203.0.113.42
```

Returns `204 No Content`. The IP is removed from Redis and the in-memory list.

> **Note:** IPs added via `sentinel.threat-detection.blocked-ips` in YAML are
> static configuration and cannot be removed via this API. Only runtime-added IPs
> (via POST) can be removed via DELETE.

---

## Rate Limit Policies

Valid values for `rateLimitPolicy` in routes and security policies:

| Tier | Requests/minute | Use case |
|------|----------------|---------|
| `ANONYMOUS` | 100 | Unauthenticated public endpoints |
| `USER` | 1000 | Standard authenticated users |
| `PREMIUM` | 10000 | Premium tier / internal services |
| `DEFAULT` | 1000 | Fallback when no specific policy is set |

Thresholds are configured in `application.yml` under `sentinel.rate-limit.policies`.

---

## Error Responses

| Status | Meaning |
|--------|---------|
| 400 | Invalid request body (validation error) |
| 401 | Missing or invalid Bearer token |
| 403 | Authenticated but missing ADMIN role |
| 404 | Route / policy / key / IP not found |
| 409 | Duplicate routeId or policy for route |

All errors return:
```json
{
  "timestamp": "2025-01-01T12:00:00.000+00:00",
  "path": "/admin/routes/unknown",
  "status": 404,
  "error": "Not Found",
  "message": "Route not found: unknown"
}
```
