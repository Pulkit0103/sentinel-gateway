# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

COPY pom.xml /build/parent-pom.xml

COPY test-services/hello-service/pom.xml /build/test-services/hello-service/pom.xml
COPY test-services/hello-service/src /build/test-services/hello-service/src

COPY gateway/pom.xml /build/gateway/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    cp /build/parent-pom.xml /build/pom.xml && \
    mvn -f /build/pom.xml package -pl test-services/hello-service -am -DskipTests -q

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.title="Hello Service"
LABEL org.opencontainers.image.description="Phase 1 smoke-test downstream service"

RUN addgroup -S helloservice && adduser -S helloservice -G helloservice
USER helloservice

WORKDIR /app

COPY --from=builder --chown=helloservice:helloservice \
    /build/test-services/hello-service/target/hello-service-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
