package com.cab.booking.config;

// FILE NÀY ĐÃ ĐƯỢC DỌN DẸP — Không còn cần thiết nữa.
//
// Cấu hình bảo mật của booking-service được kế thừa HOÀN TOÀN
// từ module common (iuh.fit.common.config.SecurityConfig).
//
// Module common cung cấp:
//   - SecurityFilterChain với oauth2ResourceServer (RSA JWT — khớp với auth-service)
//   - JwtDecoder (NimbusJwtDecoder với RSA public key)
//   - Public endpoints: có thể tùy chỉnh qua app.security.public-endpoints trong application.yaml
//
// Để tùy chỉnh public endpoints, thêm vào application.yaml:
//   app:
//     security:
//       public-endpoints: /actuator/health, /swagger-ui/**, /v3/api-docs/**
//
// XEM: iuh.fit.common.config.SecurityConfig để biết chi tiết cấu hình.
//
// ĐÃ XÓA:
//   - SecurityConfig (filter chain tùy chỉnh gây xung đột anyRequest)
//   - JwtAuthFilter (dùng HMAC secret — SAI loại key với auth-service dùng RSA)
//   - JwtTokenProvider (dùng HMAC — không tương thích với token được phát hành bởi auth-service)
