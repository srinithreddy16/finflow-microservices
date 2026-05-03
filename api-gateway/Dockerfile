# ── Stage 1: Build ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy Gradle wrapper and build files first (layer caching)
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Drop optional projects not copied into the image (keeps settings valid)
RUN sed -i '/"integration-tests"/d' settings.gradle.kts

# Copy shared modules
COPY common/build.gradle.kts common/build.gradle.kts
COPY common/src common/src
COPY proto/build.gradle.kts proto/build.gradle.kts
COPY proto/src proto/src

# Copy this service
COPY api-gateway/build.gradle.kts api-gateway/build.gradle.kts
COPY api-gateway/src api-gateway/src

RUN chmod +x gradlew

# Build (skip tests in Docker — tests run in CI)
RUN ./gradlew :api-gateway:bootJar -x test --no-daemon

# ── Stage 2: Runtime ──────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root user
RUN addgroup -S finflow && adduser -S finflow -G finflow

WORKDIR /app

# Copy the fat jar from builder stage
COPY --from=builder /workspace/api-gateway/build/libs/*.jar app.jar

# Set ownership
RUN chown finflow:finflow app.jar

USER finflow

# Expose service port
EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Djava.security.egd=file:/dev/./urandom \
  --enable-preview"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
