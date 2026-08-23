# Admin API

The gateway exposes a REST admin API for managing routes, API keys, and security policies at runtime — no restart required.

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

### List routes

```
GET /admin/routes
```

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/routes
```

Response:
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

### Create route

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
    "methods": ["GET"],
    "enabled": true,
    "requiredScopes": [],
    "tenantRequired": false,
    "rateLimitPolicy": "USER"
  }' \
  http://localhost:8080/admin/routes
```

### Delete route

```
DELETE /admin/routes/{routeId}
```

```bash
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/routes/my-service
```

---

## API Keys API

### List API keys

```
GET /admin/api-keys
```

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/api-keys
```

Response:
```json
[
  {
    "keyId": "key-abc-123",
    "clientId": "mobile-app",
    "tenantId": "tenant-acme",
    "status": "ACTIVE",
    "expiresAt": "2025-01-01T00:00:00Z",
    "scopes": ["user:read", "order:read"]
  }
]
```

### Create API key

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
    "scopes": ["user:read"],
    "expiresAt": "2026-01-01T00:00:00Z"
  }' \
  http://localhost:8080/admin/api-keys
```

Response includes the **raw key value** — this is shown only once:
```json
{
  "keyId": "key-xyz-789",
  "rawKey": "sk_live_...",
  "clientId": "my-service-client",
  "tenantId": "tenant-acme",
  "status": "ACTIVE"
}
```

### Revoke API key

```
DELETE /admin/api-keys/{keyId}
```

```bash
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/api-keys/key-abc-123
```

---

## Policies API

### List policies

```
GET /admin/policies
```

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/policies
```

Response:
```json
[
  {
    "policyId": "payment-service-policy",
    "routeId": "payment-service",
    "requireMfa": false,
    "requestSigningRequired": false,
    "requiredScopes": [],
    "allowedMethods": ["GET", "POST"],
    "rateLimitPolicy": "PREMIUM"
  }
]
```

### Create policy

```
POST /admin/policies
Content-Type: application/json
```

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "policyId": "my-policy",
    "routeId": "my-service",
    "requireMfa": false,
    "requestSigningRequired": true,
    "requiredScopes": ["my:read"],
    "allowedMethods": ["GET"],
    "rateLimitPolicy": "USER"
  }' \
  http://localhost:8080/admin/policies
```

### Delete policy

```
DELETE /admin/policies/{policyId}
```

```bash
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/policies/my-policy
```

---

## Error Responses

| Status | Code | Meaning |
|---|---|---|
| 401 | `UNAUTHORIZED` | Missing or invalid Bearer token |
| 403 | `FORBIDDEN` | Authenticated but not ADMIN role |
| 404 | `NOT_FOUND` | Route / key / policy not found |
| 409 | `CONFLICT` | Duplicate ID |
| 422 | `VALIDATION_ERROR` | Invalid request body |

All errors return:
```json
{
  "timestamp": "...",
  "requestId": "req-...",
  "status": 403,
  "error": "FORBIDDEN",
  "code": "INSUFFICIENT_ROLE",
  "message": "Admin role required"
}
```
