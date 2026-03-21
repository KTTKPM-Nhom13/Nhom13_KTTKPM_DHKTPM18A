
# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# 1. Cài đặt module 'common' vào local repository của container
COPY common/pom.xml common/
COPY common/src common/src
RUN mvn -f common/pom.xml clean install -DskipTests

# 2. Build 'auth_service'
COPY service/auth_service/pom.xml service/auth_service/
COPY service/auth_service/src service/auth_service/src
RUN mvn -f service/auth_service/pom.xml clean package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/service/auth_service/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]