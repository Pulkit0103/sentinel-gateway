FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml /build/parent-pom.xml
COPY test-services/payment-service/pom.xml /build/test-services/payment-service/pom.xml
COPY test-services/payment-service/src /build/test-services/payment-service/src
COPY gateway/pom.xml /build/gateway/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    cp /build/parent-pom.xml /build/pom.xml && \
    mvn -f /build/pom.xml package -pl test-services/payment-service -am -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
LABEL org.opencontainers.image.title="Payment Service"
LABEL org.opencontainers.image.description="Phase 2 test downstream service — payments"
RUN addgroup -S paymentservice && adduser -S paymentservice -G paymentservice
USER paymentservice
WORKDIR /app
COPY --from=builder --chown=paymentservice:paymentservice \
    /build/test-services/payment-service/target/payment-service-*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
