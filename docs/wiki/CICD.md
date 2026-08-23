# CI/CD

## GitHub Actions Pipelines

Two workflows live in `.github/workflows/`.

---

## `ci.yml` — Continuous Integration

**Trigger:** Push or pull request to any branch.

### Steps

1. **Checkout** — Full clone with LFS
2. **Set up Java 17** — `eclipse-temurin` distribution
3. **Maven build** — `mvn clean package -DskipTests`
4. **Maven test** — `mvn test` (all modules)
5. **OWASP Dependency Check** — flags known CVEs in dependencies; fails on CVSS ≥ 7
6. **Docker build (gateway)** — `docker buildx build` to verify the Dockerfile compiles
7. **Docker build (test services)** — builds hello/user/order/payment service images
8. **Upload test reports** — Surefire XML reports uploaded as artifacts

### Badge

```markdown
[![Build](https://github.com/Pulkit0103/sentinel-gateway/actions/workflows/ci.yml/badge.svg)](https://github.com/Pulkit0103/sentinel-gateway/actions)
```

---

## `release.yml` — Release Pipeline

**Trigger:** Tag push matching `v*` (e.g., `v1.0.0`).

### Steps

1. **Checkout**
2. **Set up Java 17**
3. **Maven build + test** — full build including tests
4. **OWASP Dependency Check** — must pass before publish
5. **Log in to GHCR** — GitHub Container Registry
6. **Docker build + push** — gateway image pushed as `ghcr.io/pulkit0103/sentinel-gateway:<tag>` and `latest`
7. **Helm package** — packages the Helm chart as `sentinel-gateway-<version>.tgz`
8. **Create GitHub Release** — attaches Helm chart archive and changelog

### Creating a Release

```bash
git tag v1.0.0
git push origin v1.0.0
```

The release pipeline fires automatically and publishes the Docker image + Helm chart.

---

## OWASP Dependency Check

The OWASP check scans all Maven dependencies against the NVD CVE database. Suppression rules for known false positives are in `.github/owasp-suppressions.xml`.

To run locally:

```bash
mvn org.owasp:dependency-check-maven:check \
  -DsuppressionFiles=.github/owasp-suppressions.xml \
  -DfailBuildOnCVSS=7
```

The HTML report is generated at `target/dependency-check-report.html`.
