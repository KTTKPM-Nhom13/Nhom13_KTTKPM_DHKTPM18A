package iuh.fit.auth_service.controller;

import iuh.fit.auth_service.service.JwtService;
import iuh.fit.common.dto.response.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class TestController {
    @Autowired
    private JwtService jwtService;

    @GetMapping("/token")
    public String genToken() {
        return jwtService.generateToken("tai");
    }

    @GetMapping("/public")
    public ApiResponse<String> testPublic() {
        return ApiResponse.<String>builder()
                .result("")
                .build();
    }

    @GetMapping("/verify")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> testVerify(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.<Map<String, Object>>builder()
                .result(Map.of(
                        "status", "Xác thực thành công!",
                        "userId", jwt.getSubject(),
                        "issuer", jwt.getClaim("iss"),
                        "allClaims", jwt.getClaims()
                ))
                .build();
    }
}
