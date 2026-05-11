# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# --- BƯỚC 1: CHUẨN BỊ COMMON ---
COPY common/pom.xml ./common/
RUN mvn -f ./common/pom.xml dependency:resolve dependency:resolve-plugins -U -B 2>&1

COPY common/src ./common/src
RUN mvn -f ./common/pom.xml clean install -DskipTests -U -B 2>&1

# --- BƯỚC 2: CHUẨN BỊ BOOKING-SERVICE ---
COPY booking-service/pom.xml ./booking-service/
RUN mvn -f ./booking-service/pom.xml dependency:resolve dependency:resolve-plugins -U -B 2>&1

# --- BƯỚC 3: COPY CODE BOOKING-SERVICE VÀ BUILD ---
COPY booking-service/src ./booking-service/src
RUN mvn -f ./booking-service/pom.xml clean package -DskipTests -U -B 2>&1

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy file JAR cuối cùng
COPY --from=builder /app/booking-service/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]