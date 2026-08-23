FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml /build/parent-pom.xml
COPY test-services/order-service/pom.xml /build/test-services/order-service/pom.xml
COPY test-services/order-service/src /build/test-services/order-service/src
COPY gateway/pom.xml /build/gateway/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    cp /build/parent-pom.xml /build/pom.xml && \
    mvn -f /build/pom.xml package -pl test-services/order-service -am -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
LABEL org.opencontainers.image.title="Order Service"
LABEL org.opencontainers.image.description="Phase 2 test downstream service — orders"
RUN addgroup -S orderservice && adduser -S orderservice -G orderservice
USER orderservice
WORKDIR /app
COPY --from=builder --chown=orderservice:orderservice \
    /build/test-services/order-service/target/order-service-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
