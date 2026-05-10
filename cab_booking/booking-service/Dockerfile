FROM eclipse-temurin:21-jre-alpine

# Cài đặt timezone (Tùy chọn nhưng rất quan trọng cho các sự kiện thời gian)
RUN apk add --no-cache tzdata
ENV TZ=Asia/Ho_Chi_Minh

# Tạo thư mục làm việc trong container
WORKDIR /app

# Copy file JAR đã được build từ target vào container
COPY target/*-SNAPSHOT.jar app.jar

# Mở port
EXPOSE 8081

# Lệnh chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]