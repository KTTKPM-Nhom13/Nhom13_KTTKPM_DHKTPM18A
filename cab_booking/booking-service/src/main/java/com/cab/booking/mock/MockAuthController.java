package com.cab.booking.mock;

import com.cab.booking.common.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Mock Auth Service.
 * Giả lập API đăng nhập và lấy JWT token.
 * <p>
 * Trong thực tế, Auth Service sẽ xác thực user/password
 * và trả về JWT chứa customerId, roles...
 */
@RestController
@RequestMapping("/api/mock/auth")
public class MockAuthController {

    private static final List<MockUser> MOCK_USERS = List.of(
            MockUser.builder().id("cus-mock-001").name("Nguyễn Văn A").phone("0909123456").role("CUSTOMER").build(),
            MockUser.builder().id("cus-mock-002").name("Trần Thị B").phone("0912345678").role("CUSTOMER").build(),
            MockUser.builder().id("drv-mock-001").name("Lê Văn C").phone("0923456789").role("DRIVER").vehicleId("51A-12345").build()
    );

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        MockUser user = MOCK_USERS.stream()
                .filter(u -> u.getPhone().equals(request.getPhone()))
                .findFirst()
                .orElse(null);

        if (user == null) {
            return ApiResponse.error(401, "Số điện thoại hoặc mật khẩu không đúng.");
        }

        String token = generateMockJwt(user);

        return ApiResponse.success(LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(3600)
                .userId(user.getId())
                .name(user.getName())
                .role(user.getRole())
                .build());
    }

    /** Mock xác thực token — trả về user info */
    @GetMapping("/me")
    public ApiResponse<MockUser> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ApiResponse.error(401, "Missing or invalid Authorization header.");
        }

        String token = authHeader.substring(7);
        MockUser user = parseUserFromMockToken(token);
        if (user == null) {
            return ApiResponse.error(401, "Invalid or expired token.");
        }
        return ApiResponse.success(user);
    }

    private String generateMockJwt(MockUser user) {
        String header = java.util.Base64.getEncoder()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = java.util.Base64.getEncoder()
                .encodeToString(("{\"sub\":\"" + user.getId()
                        + "\",\"name\":\"" + user.getName()
                        + "\",\"role\":\"" + user.getRole()
                        + "\",\"exp\":" + (System.currentTimeMillis() / 1000 + 3600) + "}").getBytes());
        return header + "." + payload + ".MOCK_SIGNATURE";
    }

    private MockUser parseUserFromMockToken(String token) {
        try {
            String payload = token.split("\\.")[1];
            String decoded = new String(java.util.Base64.getDecoder().decode(payload));
            String userId = decoded.split("\"sub\":\"")[1].split("\"")[0];
            return MOCK_USERS.stream().filter(u -> u.getId().equals(userId)).findFirst().orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    // ========== DTO ==========
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        private String phone;
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String accessToken;
        private String tokenType;
        private int expiresIn;
        private String userId;
        private String name;
        private String role;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MockUser {
        private String id;
        private String name;
        private String phone;
        private String role;
        private String vehicleId;
    }
}
