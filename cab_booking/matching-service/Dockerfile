# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# --- Step 1: Resolve dependencies ---
COPY matching-service/pom.xml ./matching-service/
RUN mvn -f ./matching-service/pom.xml dependency:resolve dependency:resolve-plugins -U -B 2>&1

# --- Step 2: Copy source and package ---
COPY matching-service/src ./matching-service/src
RUN mvn -f ./matching-service/pom.xml clean package -DskipTests -U -B 2>&1

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache tzdata
ENV TZ=Asia/Ho_Chi_Minh

COPY --from=builder /app/matching-service/target/*.jar app.jar

EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
