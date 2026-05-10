# Multi-stage build for Pricing Service
# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Install build dependencies
RUN apk add --no-cache curl

# Copy common module first (dependency resolution)
COPY common/pom.xml ./common/
RUN mvn -f ./common/pom.xml dependency:resolve dependency:resolve-plugins -U -B

# Copy common source and build
COPY common/src ./common/src
RUN mvn -f ./common/pom.xml clean install -DskipTests -U -B

# Copy pricing-service pom and resolve dependencies
COPY Pricing-Service/pom.xml ./pricing-service/
RUN mvn -f ./pricing-service/pom.xml dependency:resolve dependency:resolve-plugins -U -B

# Copy pricing-service source code
COPY Pricing-Service/src ./pricing-service/src

# Build the pricing-service
RUN mvn -f ./pricing-service/pom.xml clean package -DskipTests -U -B

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -s /bin/sh -D appuser

# Copy the built JAR from builder stage
COPY --from=builder /app/pricing-service/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8087

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8087/actuator/health || exit 1

# JVM optimization for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
