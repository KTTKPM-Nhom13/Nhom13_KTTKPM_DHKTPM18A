# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# 1. Chỉ copy cấu hình Maven để tải thư viện trước
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# 2. Tải toàn bộ thư viện Eureka về Cache (Layer này sẽ được giữ lại)
RUN ./mvnw dependency:go-offline -B

# 3. Copy code và build JAR
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy JAR từ stage build sang
COPY --from=build /app/target/*.jar app.jar

# Port 8761 là cổng truyền thống của Eureka
EXPOSE 8761

# Tối ưu RAM cho Java trong Docker
ENTRYPOINT ["java", "-Xmx384m", "-XX:+UseSerialGC", "-Xss512k", "-jar", "app.jar"]