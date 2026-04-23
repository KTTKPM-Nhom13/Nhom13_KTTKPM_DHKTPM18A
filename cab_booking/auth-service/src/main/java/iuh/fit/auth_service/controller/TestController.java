package iuh.fit.auth_service.controller;

import iuh.fit.auth_service.utils.JwtUtil;
import iuh.fit.common.dto.response.ApiResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TestController {
    JwtUtil jwtUtil;
    JwtDecoder jwtDecoder;

    @GetMapping("/token")
    public ResponseEntity<String> genToken(
            @RequestHeader(value = "X-Debug-Trace-Id", required = false) String traceId) {
        Authentication authen = new UsernamePasswordAuthenticationToken(
                "tai",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))

        );
        String token = jwtUtil.generateToken(authen);

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Served-By", "AUTH-SERVICE");
        if (traceId != null && !traceId.isBlank()) {
            headers.add("X-Debug-Trace-Id", traceId);
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(token);
    }

    @GetMapping("/verify")
    public ApiResponse<Map<String, Object>> testVerify(@RequestBody String token) {
        Jwt jwt = jwtDecoder.decode(token);
        return ApiResponse.<Map<String, Object>>builder()
                .result(Map.of(
                        "status", "Xác thực thành công!",
                        "userId", jwt.getSubject(),
                        "issuer", jwt.getClaim("iss"),
                        "allClaims", jwt.getClaims()))
                .build();
    }
}
