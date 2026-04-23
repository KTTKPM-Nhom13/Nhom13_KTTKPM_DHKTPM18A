# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Clear Maven cache để tránh lỗi artifact cũ (chỉ xóa file, không chạy mvn vì chưa có pom.xml)

# --- BƯỚC 1: CHUẨN BỊ COMMON ---
# Chỉ copy pom của common để install thư viện trước
COPY common/pom.xml ./common/
RUN mvn -f ./common/pom.xml dependency:resolve dependency:resolve-plugins -U -B 2>&1

# Copy code common và install vào local repo trong Docker
COPY common/src ./common/src
RUN mvn -f ./common/pom.xml clean install -DskipTests -U -B 2>&1

# --- BƯỚC 2: CHUẨN BỊ AUTH-SERVICE ---
# Chỉ copy pom của auth-service
COPY auth-service/pom.xml ./auth-service/
# Tải thư viện của auth-service (nó sẽ nhận diện common đã install ở trên)
RUN mvn -f ./auth-service/pom.xml dependency:resolve dependency:resolve-plugins -U -B 2>&1

# --- BƯỚC 3: COPY CODE AUTH-SERVICE VÀ BUILD ---
COPY auth-service/src ./auth-service/src
RUN mvn -f ./auth-service/pom.xml clean package -DskipTests -U -B 2>&1
# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Chỉ copy file JAR cuối cùng
COPY --from=builder /app/auth-service/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]