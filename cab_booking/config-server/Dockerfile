# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# 1. Chỉ copy pom.xml để tải thư viện trước (Tạo layer cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. Sau đó mới copy source code và build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run
# Alpine runtime is smaller and usually pulls faster/more reliably than jammy.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Tạo thư mục chứa các file .yml cấu hình (nếu Tài dùng file hệ thống thay vì Git)
RUN mkdir -p /app/config

EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
