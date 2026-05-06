# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# --- BƯỚC 1: CHUẨN BỊ COMMON ---
COPY common/pom.xml ./common/
RUN mvn -f ./common/pom.xml dependency:resolve dependency:resolve-plugins -U -B 2>&1

COPY common/src ./common/src
RUN mvn -f ./common/pom.xml clean install -DskipTests -U -B 2>&1

# --- BƯỚC 2: CHUẨN BỊ NOTIFICATION-SERVICE ---
COPY notification-service/pom.xml ./notification-service/
RUN mvn -f ./notification-service/pom.xml dependency:resolve dependency:resolve-plugins -U -B 2>&1

# --- BƯỚC 3: COPY CODE NOTIFICATION-SERVICE VÀ BUILD ---
COPY notification-service/src ./notification-service/src
RUN mvn -f ./notification-service/pom.xml clean package -DskipTests -U -B 2>&1

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/notification-service/target/*.jar app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
