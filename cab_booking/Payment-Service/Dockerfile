# Multi-stage build for Payment Service
# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# --- Step 1: Build common module first ---
COPY common/pom.xml ./common/
RUN mvn -f ./common/pom.xml dependency:resolve dependency:resolve-plugins -U -B

COPY common/src ./common/src
RUN mvn -f ./common/pom.xml clean install -DskipTests -U -B

# --- Step 2: Prepare Payment-Service ---
COPY Payment-Service/pom.xml ./payment-service/
RUN mvn -f ./payment-service/pom.xml dependency:resolve dependency:resolve-plugins -U -B

# --- Step 3: Copy Payment-Service source and build ---
COPY Payment-Service/src ./payment-service/src
RUN mvn -f ./payment-service/pom.xml clean package -DskipTests -U -B

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -s /bin/sh -D appuser

COPY --from=builder /app/payment-service/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8088

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8088/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
