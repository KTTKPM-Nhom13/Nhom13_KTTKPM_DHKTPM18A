package com.cab.booking.config;

// FILE NÀY ĐÃ BỊ VÔ HIỆU HÓA.
//
// JwtTokenProvider dùng HMAC-SHA256 với secret key (app.security.jwt.secret).
// auth-service ký JWT bằng RSA private key — HMAC và RSA là HAI loại thuật toán khác nhau,
// dẫn đến SignatureException khi validate.
//
// Khi sử dụng iuh.fit.common.config.SecurityConfig:
//   - JwtDecoder được cấu hình tự động với RSA public key từ certs/public_key.pem
//     hoặc từ biến môi trường JWT_PRIVATE_KEY (sẽ derive public key từ private key).
//   - Không cần JwtTokenProvider thủ công nữa.
//   - Để đọc claims từ JWT trong service layer, inject SecurityContext:
//       Authentication auth = SecurityContextHolder.getContext().getAuthentication();
//       Jwt jwt = (Jwt) auth.getPrincipal();
//       String userId = jwt.getSubject();
