# Kubernetes & Helm

## Prerequisites

- `kubectl` configured against a cluster
- `helm` 3.x
- Container images pushed to a registry (or use local `kind` / `minikube`)

## Deploy with Raw Kubernetes Manifests

```bash
kubectl apply -f deployment/kubernetes/
```

This creates:
- `Namespace`: sentinel-gateway
- `Deployment`: sentinel-gateway (2 replicas)
- `Service`: ClusterIP on port 8080
- `ConfigMap`: application config
- `Secret`: database, Redis, Keycloak credentials
- `HorizontalPodAutoscaler`: scales 2–10 pods on CPU ≥ 70%
- `Ingress`: routes traffic with TLS termination

## Deploy with Helm

```bash
# Install with defaults
helm install sentinel-gateway deployment/helm/sentinel-gateway

# Install with custom values
helm install sentinel-gateway deployment/helm/sentinel-gateway \
  --set gateway.replicaCount=3 \
  --set gateway.image.tag=v1.0.0 \
  --set keycloak.issuer=https://auth.example.com/realms/sentinel \
  --set redis.password=my-secret

# Upgrade
helm upgrade sentinel-gateway deployment/helm/sentinel-gateway \
  --set gateway.image.tag=v1.1.0

# Uninstall
helm uninstall sentinel-gateway

# Dry run (preview manifests)
helm install sentinel-gateway deployment/helm/sentinel-gateway --dry-run --debug
```

## Key Helm Values

| Value | Default | Description |
|---|---|---|
| `gateway.replicaCount` | `2` | Number of gateway pods |
| `gateway.image.repository` | `ghcr.io/pulkit0103/sentinel-gateway` | Container image |
| `gateway.image.tag` | `latest` | Image tag |
| `gateway.resources.requests.memory` | `256Mi` | Memory request |
| `gateway.resources.limits.memory` | `512Mi` | Memory limit |
| `keycloak.issuer` | *(required)* | Keycloak realm URL |
| `keycloak.jwksUri` | *(required)* | JWKS endpoint |
| `redis.host` | *(required)* | Redis hostname |
| `redis.password` | *(required)* | Redis password |
| `database.url` | *(required)* | R2DBC PostgreSQL URL |
| `database.password` | *(required)* | PostgreSQL password |
| `rateLimiting.enabled` | `true` | Enable Redis rate limiting in K8s |
| `quotas.enabled` | `true` | Enable quota enforcement |
| `threatDetection.enabled` | `true` | Enable WAF detection |
| `audit.kafka.enabled` | `true` | Enable Kafka audit events |
| `kafka.bootstrapServers` | *(required)* | Kafka bootstrap address |

## Health Probes

The Helm chart wires Kubernetes liveness and readiness probes to Spring Boot Actuator groups:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 45
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 5
```

Kubernetes will not send traffic to a pod until its readiness probe passes, and will restart pods that fail the liveness probe.

## Graceful Shutdown

The gateway is configured for graceful shutdown:

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

When Kubernetes sends `SIGTERM`, the gateway:
1. Sets readiness state to `REFUSING_TRAFFIC` (stops receiving new connections)
2. Completes in-flight requests (up to 30 seconds)
3. Exits cleanly

The `terminationGracePeriodSeconds` in the Deployment spec should be set to ≥ 30 seconds.

## Prometheus Monitoring

The Helm chart optionally creates a `ServiceMonitor` for Prometheus Operator:

```bash
helm install sentinel-gateway deployment/helm/sentinel-gateway \
  --set serviceMonitor.enabled=true \
  --set serviceMonitor.namespace=monitoring
```

The gateway exposes metrics at `/actuator/prometheus`. The pre-provisioned Grafana dashboard (in Docker Compose) can be imported manually into a production Grafana instance.
