package iuh.fit.auth_service.controller;

import iuh.fit.auth_service.service.AuthService;
import iuh.fit.common.dto.response.ApiResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth/admin")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminAuthController {
    AuthService authService;

    @PostMapping("/users/{userId}/block")
    public ApiResponse<Void> blockUser(@PathVariable UUID userId) {
        authService.blockUser(userId);
        return ApiResponse.<Void>builder()
                .message("User blocked successfully")
                .build();
    }

    @PostMapping("/users/{userId}/unblock")
    public ApiResponse<Void> unblockUser(@PathVariable UUID userId) {
        authService.unblockUser(userId);
        return ApiResponse.<Void>builder()
                .message("User unblocked successfully")
                .build();
    }
}
