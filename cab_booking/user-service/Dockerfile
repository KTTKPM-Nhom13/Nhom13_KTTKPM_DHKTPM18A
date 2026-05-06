# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# --- Step 1: Build common first so user-service can reuse shared classes ---
COPY common/pom.xml ./common/
RUN mvn -f ./common/pom.xml dependency:resolve dependency:resolve-plugins -U -B 2>&1

COPY common/src ./common/src
RUN mvn -f ./common/pom.xml clean install -DskipTests -U -B 2>&1

# --- Step 2: Resolve user-service dependencies ---
COPY user-service/pom.xml ./user-service/
RUN mvn -f ./user-service/pom.xml dependency:resolve dependency:resolve-plugins -U -B 2>&1

# --- Step 3: Copy source and package user-service ---
COPY user-service/src ./user-service/src
RUN mvn -f ./user-service/pom.xml clean package -DskipTests -U -B 2>&1

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/user-service/target/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
