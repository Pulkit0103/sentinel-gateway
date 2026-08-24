# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copy parent POM first for layer caching
COPY pom.xml /build/parent-pom.xml

# Copy gateway module
COPY gateway/pom.xml /build/gateway/pom.xml
COPY gateway/src /build/gateway/src

# All test-service POMs needed for multi-module resolution
COPY test-services/hello-service/pom.xml /build/test-services/hello-service/pom.xml
COPY test-services/user-service/pom.xml /build/test-services/user-service/pom.xml
COPY test-services/order-service/pom.xml /build/test-services/order-service/pom.xml
COPY test-services/payment-service/pom.xml /build/test-services/payment-service/pom.xml

# Resolve dependencies
RUN --mount=type=cache,target=/root/.m2 \
    cp /build/parent-pom.xml /build/pom.xml && \
    mvn -f /build/pom.xml dependency:go-offline -pl gateway -am -q

# Build gateway only
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f /build/pom.xml package -pl gateway -am -DskipTests -q

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.title="Sentinel Gateway"
LABEL org.opencontainers.image.description="Zero-Trust API Security and Traffic Management Platform"
LABEL org.opencontainers.image.version="0.1.0"

# Non-root user for security
RUN addgroup -S sentinelgateway && adduser -S sentinelgateway -G sentinelgateway
USER sentinelgateway

WORKDIR /app

COPY --from=builder --chown=sentinelgateway:sentinelgateway \
    /build/gateway/target/sentinel-gateway-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
