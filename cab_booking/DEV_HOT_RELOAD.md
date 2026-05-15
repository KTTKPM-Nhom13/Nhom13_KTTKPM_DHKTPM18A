# Hot Reload cho Backend

Project này có nhiều service Spring Boot. Để có trải nghiệm giống `nodemon`, các service backend đã được thêm `spring-boot-devtools`.

## Những service đã hỗ trợ auto reload

- `config-server`
- `eureka`
- `api-gateway`
- `auth-service`
- `email-service`
- `user-service`
- `driver-service`
- `notification-service`
- `review-service`
- `booking-service`
- `Pricing-Service`
- `Payment-Service`
- `matching-service`
- `ride-service`

## Cách chạy để tự reload khi sửa code

Devtools chỉ hoạt động ở chế độ phát triển. Hãy chạy service bằng Maven hoặc IDE, không chạy bằng `java -jar`.

Ví dụ:

```powershell
cd cab_booking/auth-service
./mvnw spring-boot:run
```

Hoặc với Windows PowerShell:

```powershell
cd cab_booking/auth-service
.\mvnw.cmd spring-boot:run
```

Khi lưu file Java/resources:

- VS Code sẽ tự build lại nhờ `java.autobuild.enabled=true`
- Spring Boot DevTools sẽ phát hiện classpath thay đổi
- Service sẽ tự restart nhanh, không cần bạn stop/start thủ công

## Lưu ý quan trọng

- `frontend` đã có hot reload sẵn qua `npm start`
- Nếu bạn chạy service bằng Docker image đang đóng gói `jar`, code thay đổi trong máy sẽ không tự reload
- Nếu bạn chạy bằng IntelliJ IDEA, hãy bật `Build project automatically` để có hiệu ứng tương tự
- Nếu sửa file cấu hình ngoài classpath hoặc thay đổi dependency Maven, vẫn nên chạy lại service
