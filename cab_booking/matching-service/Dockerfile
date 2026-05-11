# Sử dụng Java 21 bản JRE Alpine siêu nhẹ làm base image
FROM eclipse-temurin:21-jre-alpine

# Cài đặt timezone (Tùy chọn nhưng rất quan trọng cho các sự kiện thời gian)
RUN apk add --no-cache tzdata
ENV TZ=Asia/Ho_Chi_Minh

# Tạo thư mục làm việc trong container
WORKDIR /app

# Copy file JAR đã được build từ target vào container
# Đảm bảo bạn đã chạy lệnh "mvn clean package" trước khi build Docker
COPY target/*-SNAPSHOT.jar app.jar

# Mở port 8083
EXPOSE 8083

# Lệnh chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]
