# Development Guide

## Project Structure

```
sentinel-gateway/
├── gateway/                  ← Main Spring Cloud Gateway application
├── test-services/            ← Stub downstream services
├── admin-dashboard/          ← Next.js admin UI
├── infrastructure/           ← Docker Compose, Keycloak, Postgres, Prometheus, Grafana
├── deployment/               ← Kubernetes manifests + Helm chart
├── docs/                     ← Architecture docs, ADRs, wiki source
└── .github/workflows/        ← CI/CD pipelines
```

## Building

```bash
# Full build (all Maven modules)
mvn clean package

# Skip tests
mvn clean package -DskipTests

# Build a specific module
mvn clean package -pl gateway -am -DskipTests
```

## Running Tests

```bash
# All tests
mvn test

# Gateway only
mvn test -pl gateway

# Single test class
mvn test -pl gateway -Dtest=ThreatDetectionFilterTest

# Single test method
mvn test -pl gateway -Dtest=ThreatDetectionFilterTest#blocksSqlInjection
```

### Test categories (gateway module)

| Class | Coverage |
|---|---|
| `RequestIdFilterTest` | RequestId assignment |
| `RouteDefinitionTest` | Route definition model |
| `RouteRegistryTest` | Route registration |
| `GatewayRoutingIntegrationTest` | End-to-end routing |
| `AuthenticatedPrincipalTest` | Principal model |
| `JwtAuthenticationTest` | JWT validation |
| `RbacAuthorizationTest` | Role + permission checks |
| `RolePermissionsTest` | Permission matrix |
| `ApiKeyAuthenticationTest` | API key auth flow |
| `MultiTenancyTest` | Tenant isolation |
| `RateLimitResultTest` | Rate limit model |
| `RateLimitFilterTest` | Rate limit enforcement |
| `QuotaEnforcementFilterTest` | Quota enforcement |
| `HmacVerificationFilterTest` | HMAC signing + replay prevention |
| `PolicyEnforcementFilterTest` | Policy evaluation |
| `ThreatDetectionFilterTest` | WAF pattern detection |
| `AuditLoggingFilterTest` | Audit event publishing |
| `ObservabilityTest` | Prometheus metrics |
| `ServiceDiscoveryRoutingTest` | Service discovery routing |
| `ResilienceTest` | Retry + circuit breaker |
| `CircuitBreakerTest` | Circuit breaker behaviour |
| `AdminApiTest` | Admin REST API |
| `SecurityRegressionTest` | OWASP injection probes |

## Adding a New Gateway Filter

1. Create the filter class in the appropriate package under `gateway/src/main/java/com/sentinelgateway/gateway/`
2. Implement `GlobalFilter` and `Ordered`
3. Set the filter order constant (see [Architecture → Filter Chain Ordering](Architecture#globalfilter-chain-ordering))
4. Register as a `@Component` or `@Bean`
5. Write a unit test using `WebTestClient` + `MockServerWebExchange`

Example skeleton:
```java
@Component
public class MyFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return 55; // between PolicyEnforcement (50) and ThreatDetection (60)
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // ... inspection logic ...
        return chain.filter(exchange);
    }
}
```

## Adding a New Route

In `gateway/src/main/resources/application.yml` under `sentinel.gateway.routes`:

```yaml
- route-id: my-new-service
  path: /api/my/**
  service-uri: lb://my-new-service
  methods: [GET, POST]
  enabled: true
  required-scopes: []
  tenant-required: false
  rate-limit-policy: USER
```

Register the service instance under `spring.cloud.discovery.client.simple.instances`:

```yaml
my-new-service:
  - uri: ${MY_SERVICE_URL:http://localhost:9000}
```

## Admin Dashboard Development

```bash
cd admin-dashboard
npm install
npm run dev     # starts on :3001 with hot reload
npm test        # runs Jest tests
npm run build   # production build
npm run lint    # ESLint
```

The API client lives in `src/lib/api.ts`. Add new admin API calls there and create corresponding page components in `src/app/`.

## Commit Conventions

This project uses conventional commits:

```
feat: add HMAC replay protection
fix: correct tenant isolation bypass in SUPPORT role
docs: update rate limiting wiki page
test: add circuit breaker half-open transition test
chore: bump spring-boot to 3.3.5
```

## Branch Naming

Feature branches follow the pattern `feature/<phase-name>`:

```
feature/bootstrap
feature/oauth2-oidc
feature/rate-limiting
...
```

## CI Checks

The CI pipeline (`ci.yml`) runs on every push and PR:

1. `mvn clean package` — compile all modules
2. `mvn test` — unit + integration tests
3. OWASP Dependency Check — flag known CVEs in dependencies
4. `docker buildx build` — verify all Dockerfiles build successfully

PRs are blocked from merging if any CI step fails.
