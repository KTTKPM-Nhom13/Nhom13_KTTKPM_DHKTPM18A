package iuh.fit.auth_service.controller;

import iuh.fit.auth_service.dto.request.InternalAccountLifecycleRequest;
import iuh.fit.auth_service.service.AuthService;
import iuh.fit.common.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/auth/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalAuthAccountController {
    AuthService authService;

    @PostMapping("/{userId}/account-lifecycle")
    public ApiResponse<Void> syncAccountLifecycle(
            @PathVariable UUID userId,
            @Valid @RequestBody InternalAccountLifecycleRequest request) {
        authService.syncAccountLifecycle(userId, request);
        return ApiResponse.<Void>builder()
                .message("Synced account lifecycle successfully")
                .build();
    }
}
