package com.cab.booking.config;

// FILE NÀY ĐÃ BỊ VÔ HIỆU HÓA.
//
// Lý do: JwtAuthFilter cũ dùng HMAC-SHA256 (symmetric secret key) để validate JWT.
// Điều này KHÔNG TƯƠNG THÍCH với auth-service vốn ký token bằng RSA private key.
// Kết quả: mọi token hợp lệ đều bị từ chối bởi filter này.
//
// Thay thế: Module common (iuh.fit.common.config.SecurityConfig) đã cấu hình
// oauth2ResourceServer với NimbusJwtDecoder dùng RSA public key — đây là cơ chế đúng.
// Spring Security tự động validate JWT qua oauth2ResourceServer, không cần filter thủ công.
//
// KHÔNG XÓA FILE NÀY nếu muốn giữ lịch sử. Nội dung bên dưới không được compile
// vì không có class declaration.
