# Stage 1: Build - Thống nhất dùng Java 21
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy file cấu hình trước để tận dụng Docker Cache
COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run - Cùng phiên bản Java 21 (dùng bản JRE cho nhẹ)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Lấy file jar đã build
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]