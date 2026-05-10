# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY common/pom.xml ./common/
RUN mvn -f ./common/pom.xml dependency:resolve dependency:resolve-plugins -U -B 2>&1

COPY common/src ./common/src
RUN mvn -f ./common/pom.xml clean install -DskipTests -U -B 2>&1

COPY driver-service/pom.xml ./driver-service/
RUN mvn -f ./driver-service/pom.xml dependency:resolve dependency:resolve-plugins -U -B 2>&1

COPY driver-service/src ./driver-service/src
RUN mvn -f ./driver-service/pom.xml clean package -DskipTests -U -B 2>&1

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/driver-service/target/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
