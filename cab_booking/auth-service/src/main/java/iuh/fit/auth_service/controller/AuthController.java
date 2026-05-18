package iuh.fit.auth_service.controller;

import iuh.fit.auth_service.dto.request.ChangePasswordRequest;
import iuh.fit.auth_service.dto.request.LoginRequest;
import iuh.fit.auth_service.dto.request.LogoutRequest;
import iuh.fit.auth_service.dto.request.RefreshTokenRequest;
import iuh.fit.auth_service.dto.request.RequestForgotPasswordOtpRequest;
import iuh.fit.auth_service.dto.request.RegisterRequest;
import iuh.fit.auth_service.dto.request.ResetForgotPasswordRequest;
import iuh.fit.auth_service.dto.request.VerifyTokenRequest;
import iuh.fit.auth_service.dto.response.AuthTokenResponse;
import iuh.fit.auth_service.dto.response.ChangePasswordResponse;
import iuh.fit.auth_service.dto.response.RegisterOtpResponse;
import iuh.fit.auth_service.service.AuthService;
import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.common.security.JwtTokenService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthController {
    AuthService authService;
    JwtTokenService jwtTokenService;

    /*
    Old registration flow kept here for reference while dev uses a single-step register endpoint.
    @PostMapping("/register/request-otp")
    public ApiResponse<RegisterOtpResponse> requestRegisterOtp(@Valid @RequestBody RequestRegisterOtpRequest request) {
        return ApiResponse.<RegisterOtpResponse>builder()
                .message("Registration OTP sent successfully")
                .result(authService.requestRegisterOtp(request))
                .build();
    }

    @PostMapping("/register/verify-otp")
    public ApiResponse<VerifyRegisterOtpResponse> verifyRegisterOtp(
            @Valid @RequestBody VerifyRegisterOtpRequest request) {
        return ApiResponse.<VerifyRegisterOtpResponse>builder()
                .message("Registration OTP verified successfully")
                .result(authService.verifyRegisterOtp(request))
                .build();
    }
    */

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.<AuthTokenResponse>builder()
                .message("Registered successfully")
                .result(authService.register(request))
                .build();
    }

    @PostMapping("/admin/register")
    public ApiResponse<AuthTokenResponse> registerAdmin(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.<AuthTokenResponse>builder()
                .message("Admin registered successfully")
                .result(authService.registerAdmin(request))
                .build();
    }


    /*
    Old alias for registration OTP flow.
    @PostMapping("/register/email")
    public ApiResponse<RegisterOtpResponse> registerByEmail(@Valid @RequestBody RequestRegisterOtpRequest request) {
        return ApiResponse.<RegisterOtpResponse>builder()
                .message("Registration OTP sent successfully")
                .result(authService.requestRegisterOtp(request))
                .build();
    }
    */

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.<AuthTokenResponse>builder()
                .message("Logged in successfully")
                .result(authService.login(request))
                .build();
    }

    @PostMapping("/password/change")
    public ApiResponse<ChangePasswordResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.<ChangePasswordResponse>builder()
                .message("Password changed successfully")
                .result(authService.changePassword(request))
                .build();
    }

    @PostMapping("/password/forgot/request-otp")
    public ApiResponse<RegisterOtpResponse> requestForgotPasswordOtp(
            @Valid @RequestBody RequestForgotPasswordOtpRequest request) {
        return ApiResponse.<RegisterOtpResponse>builder()
                .message("Forgot-password OTP sent successfully")
                .result(authService.requestForgotPasswordOtp(request))
                .build();
    }

    @PostMapping("/password/forgot/reset")
    public ApiResponse<ChangePasswordResponse> resetForgotPassword(
            @Valid @RequestBody ResetForgotPasswordRequest request) {
        return ApiResponse.<ChangePasswordResponse>builder()
                .message("Password reset successfully")
                .result(authService.resetForgotPassword(request))
                .build();
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.<AuthTokenResponse>builder()
                .message("Refreshed token successfully")
                .result(authService.refresh(request))
                .build();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.<Void>builder()
                .message("Logged out successfully")
                .build();
    }

    @GetMapping("/token")
    public ResponseEntity<String> genToken(
            @RequestHeader(value = "X-Debug-Trace-Id", required = false) String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Served-By", "AUTH-SERVICE");
        if (traceId != null && !traceId.isBlank()) {
            headers.add("X-Debug-Trace-Id", traceId);
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(authService.generateDebugToken());
    }

    @PostMapping("/verify")
    public ApiResponse<Map<String, Object>> verify(@Valid @RequestBody VerifyTokenRequest request) {
        Jwt jwt = jwtTokenService.decode(request.getToken());
        return ApiResponse.<Map<String, Object>>builder()
                .message("Verified token successfully")
                .result(Map.of(
                        "status", "success",
                        "userId", jwt.getSubject(),
                        "issuer", String.valueOf(jwt.getClaims().get("iss")),
                        "allClaims", jwt.getClaims()))
                .build();
    }
}
