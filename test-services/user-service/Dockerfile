FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build
COPY pom.xml /build/parent-pom.xml
COPY test-services/user-service/pom.xml /build/test-services/user-service/pom.xml
COPY test-services/user-service/src /build/test-services/user-service/src
COPY gateway/pom.xml /build/gateway/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    cp /build/parent-pom.xml /build/pom.xml && \
    mvn -f /build/pom.xml package -pl test-services/user-service -am -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
LABEL org.opencontainers.image.title="User Service"
LABEL org.opencontainers.image.description="Phase 2 test downstream service — users"
RUN addgroup -S userservice && adduser -S userservice -G userservice
USER userservice
WORKDIR /app
COPY --from=builder --chown=userservice:userservice \
    /build/test-services/user-service/target/user-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
