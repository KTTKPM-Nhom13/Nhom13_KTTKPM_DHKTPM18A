package iuh.fit.userservice.controller;

import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.userservice.dto.request.AdminCreateUserRequest;
import iuh.fit.userservice.entity.UserProfile;
import iuh.fit.userservice.entity.AccountLifecycleStatus;
import iuh.fit.userservice.repository.UserProfileRepository;
import iuh.fit.userservice.service.AuthAccountSyncClient;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserController {

    UserProfileRepository userProfileRepository;
    AuthAccountSyncClient authAccountSyncClient;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/users")
    public ApiResponse<List<UserProfile>> getAllUsers() {
        return ApiResponse.<List<UserProfile>>builder()
                .message("Fetched users successfully")
                .result(userProfileRepository.findAllByOrderByCreatedAtDesc())
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping({"/api/admin/users/count", "/api/users/count"})
    public ApiResponse<Long> countUsers() {
        return ApiResponse.<Long>builder()
                .message("Fetched user count")
                .result(userProfileRepository.count())
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/admin/users")
    public ApiResponse<UserProfile> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        // 1. Prepare payload for auth-service
        Map<String, Object> authPayload = new HashMap<>();
        authPayload.put("fullName", request.getFullName());
        authPayload.put("email", request.getEmail());
        authPayload.put("password", request.getPassword());
        authPayload.put("phoneNumber", request.getPhoneNumber());
        authPayload.put("avatarUrl", request.getAvatarUrl());
        authPayload.put("role", "USER");
        authPayload.put("deviceId", "ADMIN_CREATED");
        authPayload.put("platform", "WEB_ADMIN");

        // 2. Call auth-service to create AuthUser
        String externalUserId = authAccountSyncClient.registerAuthAccount(authPayload);

        // 3. Create UserProfile locally in user-service
        UserProfile profile = userProfileRepository.findByExternalUserId(externalUserId)
                .orElseGet(() -> {
                    UserProfile created = new UserProfile();
                    created.setExternalUserId(externalUserId);
                    created.setAccountStatus(AccountLifecycleStatus.ACTIVE);
                    return created;
                });
        profile.setFullName(request.getFullName());
        profile.setEmail(request.getEmail());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setAvatarUrl(request.getAvatarUrl());
        
        UserProfile savedProfile = userProfileRepository.save(profile);

        return ApiResponse.<UserProfile>builder()
                .message("Created user successfully")
                .result(savedProfile)
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/api/users/{userId}/status")
    public ApiResponse<UserProfile> updateUserStatus(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> body) {
        String statusStr = body.get("status");
        if (statusStr == null) {
            throw new IllegalArgumentException("Status is required");
        }

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User profile not found"));

        AccountLifecycleStatus status = AccountLifecycleStatus.valueOf(statusStr.toUpperCase());
        profile.setAccountStatus(status);
        UserProfile savedProfile = userProfileRepository.save(profile);

        try {
            authAccountSyncClient.syncAccountLifecycle(savedProfile);
        } catch (Exception e) {
            // Log sync error or handle if needed
        }

        return ApiResponse.<UserProfile>builder()
                .message("Updated user status successfully")
                .result(savedProfile)
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/admin/users/{userId}/block")
    public ApiResponse<UserProfile> blockUser(@PathVariable UUID userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User profile not found"));

        profile.setAccountStatus(AccountLifecycleStatus.SUSPENDED);
        UserProfile savedProfile = userProfileRepository.save(profile);

        try {
            authAccountSyncClient.syncAccountLifecycle(savedProfile);
        } catch (Exception e) {
            log.error("Failed to sync block to auth-service for user {}: {}", userId, e.getMessage());
        }

        return ApiResponse.<UserProfile>builder()
                .message("User blocked successfully")
                .result(savedProfile)
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/admin/users/{userId}/unblock")
    public ApiResponse<UserProfile> unblockUser(@PathVariable UUID userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User profile not found"));

        profile.setAccountStatus(AccountLifecycleStatus.ACTIVE);
        UserProfile savedProfile = userProfileRepository.save(profile);

        try {
            authAccountSyncClient.syncAccountLifecycle(savedProfile);
        } catch (Exception e) {
            log.error("Failed to sync unblock to auth-service for user {}: {}", userId, e.getMessage());
        }

        return ApiResponse.<UserProfile>builder()
                .message("User unblocked successfully")
                .result(savedProfile)
                .build();
    }

    @PostMapping("/internal/users")
    public ApiResponse<UserProfile> createInternalUser(@RequestBody Map<String, String> body) {
        String externalUserId = body.get("userId");
        String fullName = body.get("fullName");
        String email = body.get("email");
        String phoneNumber = body.get("phoneNumber");
        String avatarUrl = body.get("avatarUrl");

        UserProfile profile = userProfileRepository.findByExternalUserId(externalUserId)
                .orElseGet(() -> {
                    UserProfile created = new UserProfile();
                    created.setExternalUserId(externalUserId);
                    created.setAccountStatus(AccountLifecycleStatus.ACTIVE);
                    return created;
                });

        profile.setFullName(fullName);
        profile.setEmail(email);
        profile.setPhoneNumber(phoneNumber);
        profile.setAvatarUrl(avatarUrl);

        UserProfile savedProfile = userProfileRepository.save(profile);

        return ApiResponse.<UserProfile>builder()
                .message("Synchronized user profile successfully")
                .result(savedProfile)
                .build();
    }
}
