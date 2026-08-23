# Admin Dashboard

The Sentinel Gateway Admin Dashboard is a **Next.js 14 TypeScript** single-page application that connects to the gateway's REST admin API and Actuator endpoints to provide a real-time management UI.

## Stack

| Technology | Version |
|---|---|
| Next.js | 14.2 (App Router) |
| React | 18 |
| TypeScript | 5.7 |
| Tailwind CSS | 3.4 |
| Jest + Testing Library | 29 / 14 |

## Running the Dashboard

### With Docker Compose (recommended)

The dashboard starts automatically as part of the full stack:

```bash
cd infrastructure/docker-compose
docker compose up --build
```

Dashboard available at: **http://localhost:3001**

### Standalone development

```bash
cd admin-dashboard
cp .env.local.example .env.local
# Set NEXT_PUBLIC_GATEWAY_URL=http://localhost:8080

npm install
npm run dev
```

Dashboard available at: **http://localhost:3001**

### Build for production

```bash
npm run build
npm start
```

## Authentication

The dashboard calls the gateway's admin endpoints, which require a valid JWT. On first load, the dashboard shows a **Token Setup** dialog. Paste a Bearer token obtained from Keycloak:

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/sentinel/protocol/openid-connect/token \
  -d 'client_id=sentinel-gateway-client&grant_type=password' \
  -d 'username=alice&password=alice-password' \
  | jq -r .access_token)

echo $TOKEN
```

Paste this token into the dashboard's Token Setup dialog. The token is stored in `localStorage` and sent as `Authorization: Bearer <token>` on every API call.

## Pages

### Dashboard (/)

Real-time overview:

- **Gateway Health** — `UP` / `DOWN` / `DEGRADED` badge polling `/actuator/health`
- **Total Requests** — Counter from Prometheus metric `gateway.requests.total`
- **Routes** — Count of registered routes from `/admin/routes`
- **Active API Keys** — Count of `ACTIVE` keys from `/admin/api-keys`
- **Policies** — Count of policies from `/admin/policies`

### Routes (/routes)

Table of all gateway routes with columns:
- Route ID
- Path pattern
- Target service
- Allowed HTTP methods
- Enabled / Disabled status

### API Keys (/api-keys)

Table of all API keys with columns:
- Key ID (masked)
- Client ID
- Tenant ID
- Status (ACTIVE / REVOKED / EXPIRED)
- Expiry date

### Policies (/policies)

Table of security policies with columns:
- Policy ID
- Route ID it applies to
- Required scopes
- MFA required
- Request signing required
- Rate limit tier

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `NEXT_PUBLIC_GATEWAY_URL` | `http://localhost:8080` | Gateway base URL for API calls |

## Running Tests

```bash
cd admin-dashboard
npm test
```

4 component tests are included covering `NavBar` rendering and active link highlighting.
