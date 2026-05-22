# syntax=docker/dockerfile:1
# ══════════════════════════════════════════════════════════════════
# Stage 1: BUILD — Compiles the application and produces a fat JAR
# ══════════════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy Maven wrapper files FIRST to cache dependency download layers
COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies offline utilizing cache mounts to speed up rebuilds
RUN --mount=type=cache,target=/root/.m2/repository \
    chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build package
COPY src src
RUN --mount=type=cache,target=/root/.m2/repository \
    ./mvnw package -DskipTests -B \
    && mv target/*.jar target/app.jar

# ══════════════════════════════════════════════════════════════════
# Stage 2: RUN — Lightweight runtime image (JRE only)
# ══════════════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install curl for health check
RUN apk add --no-cache curl

# Security: run as non-root user 'orion'
RUN addgroup -S orion && adduser -S orion -G orion -u 1001
WORKDIR /app

# Copy the fat JAR from the builder stage with proper ownership
COPY --from=builder --chown=orion:orion /build/target/app.jar app.jar

# Switch to non-root user
USER orion

# Port exposure
EXPOSE 8081

# JVM Memory & Performance Optimization
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=65.0 \
    -XX:InitialRAMPercentage=40.0 \
    -XX:MaxMetaspaceSize=160m \
    -XX:+UseG1GC \
    -XX:G1HeapRegionSize=4m \
    -Xss256k \
    -XX:+ExitOnOutOfMemoryError \
    -Dserver.tomcat.threads.max=50"

# Healthcheck hitting Actuator endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# Entrypoint using shell form to expand JAVA_OPTS
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
