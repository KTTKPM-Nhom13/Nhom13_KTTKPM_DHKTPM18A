FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY common/pom.xml ./common/
RUN mvn -f ./common/pom.xml dependency:resolve dependency:resolve-plugins -U -B 2>&1

COPY common/src ./common/src
RUN mvn -f ./common/pom.xml clean install -DskipTests -U -B 2>&1

COPY api-gateway/pom.xml ./api-gateway/
RUN mvn -f ./api-gateway/pom.xml dependency:resolve dependency:resolve-plugins -U -B 2>&1

COPY api-gateway/src ./api-gateway/src
RUN mvn -f ./api-gateway/pom.xml clean package -DskipTests -U -B 2>&1

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/api-gateway/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
