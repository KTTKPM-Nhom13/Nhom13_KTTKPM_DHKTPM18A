package iuh.fit.driverservice.controller;

import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.driverservice.dto.request.AdminCreateDriverRequest;
import iuh.fit.driverservice.entity.DriverProfile;
import iuh.fit.driverservice.entity.DriverVerificationStatus;
import iuh.fit.driverservice.repository.DriverProfileRepository;
import iuh.fit.driverservice.service.AuthAccountSyncClient;
import iuh.fit.driverservice.service.DriverProfileService;
import iuh.fit.driverservice.dto.response.DriverRevenueStatsResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iuh.fit.driverservice.entity.AccountLifecycleStatus;

@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminDriverController {

    DriverProfileRepository driverProfileRepository;
    DriverProfileService driverProfileService;
    AuthAccountSyncClient authAccountSyncClient;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/drivers")
    public ApiResponse<List<DriverProfile>> getAllDrivers() {
        return ApiResponse.<List<DriverProfile>>builder()
                .message("Fetched drivers successfully")
                .result(driverProfileRepository.findAllByOrderByCreatedAtDesc())
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/drivers/revenue/stats")
    public ApiResponse<List<DriverRevenueStatsResponse>> getAllDriversRevenueStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        if (start == null) start = LocalDateTime.now().minusMonths(1);
        if (end == null) end = LocalDateTime.now();

        return ApiResponse.<List<DriverRevenueStatsResponse>>builder()
                .message("Fetched all drivers revenue stats successfully")
                .result(driverProfileService.getAllDriversRevenueStats(start, end))
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping({"/api/admin/drivers/count", "/api/drivers/count"})
    public ApiResponse<Long> countDrivers() {
        return ApiResponse.<Long>builder()
                .message("Fetched driver count")
                .result(driverProfileRepository.count())
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/admin/drivers")
    public ApiResponse<DriverProfile> createDriver(@Valid @RequestBody AdminCreateDriverRequest request) {
        // 1. Prepare payload for auth-service
        Map<String, Object> authPayload = new HashMap<>();
        authPayload.put("fullName", request.getFullName());
        authPayload.put("email", request.getEmail());
        authPayload.put("password", request.getPassword());
        authPayload.put("phoneNumber", request.getPhoneNumber());
        authPayload.put("avatarUrl", request.getAvatarUrl());
        authPayload.put("role", "DRIVER");
        authPayload.put("deviceId", "ADMIN_CREATED");
        authPayload.put("platform", "WEB_ADMIN");

        // 2. Call auth-service to create AuthUser
        String externalUserId = authAccountSyncClient.registerAuthAccount(authPayload);

        // 3. Create DriverProfile locally in driver-service
        DriverProfile profile = driverProfileRepository.findByExternalUserId(externalUserId)
                .orElseGet(() -> {
                    DriverProfile created = new DriverProfile();
                    created.setExternalUserId(externalUserId);
                    created.setVerificationStatus(DriverVerificationStatus.PENDING);
                    return created;
                });
        profile.setFullName(request.getFullName());
        profile.setEmail(request.getEmail());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setAvatarUrl(request.getAvatarUrl());
        
        DriverProfile savedProfile = driverProfileRepository.save(profile);

        return ApiResponse.<DriverProfile>builder()
                .message("Created driver successfully")
                .result(savedProfile)
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/api/drivers/{driverId}/verification")
    public ApiResponse<DriverProfile> updateDriverVerification(
            @PathVariable UUID driverId,
            @RequestBody Map<String, String> body) {
        String statusStr = body.get("status");
        if (statusStr == null) {
            throw new IllegalArgumentException("Status is required");
        }

        DriverProfile profile = driverProfileRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver profile not found"));

        DriverVerificationStatus status = DriverVerificationStatus.valueOf(statusStr.toUpperCase());
        profile.setVerificationStatus(status);
        if (status == DriverVerificationStatus.APPROVED) {
            profile.setApprovedAt(java.time.LocalDateTime.now());
        }

        DriverProfile savedProfile = driverProfileRepository.save(profile);

        return ApiResponse.<DriverProfile>builder()
                .message("Updated driver verification status successfully")
                .result(savedProfile)
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/admin/drivers/{driverId}/block")
    public ApiResponse<DriverProfile> blockDriver(@PathVariable UUID driverId) {
        DriverProfile profile = driverProfileRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver profile not found"));

        profile.setAccountStatus(AccountLifecycleStatus.SUSPENDED);
        DriverProfile savedProfile = driverProfileRepository.save(profile);

        try {
            authAccountSyncClient.syncAccountLifecycle(savedProfile);
        } catch (Exception e) {
            log.error("Failed to sync block to auth-service for driver {}: {}", driverId, e.getMessage());
        }

        return ApiResponse.<DriverProfile>builder()
                .message("Driver blocked successfully")
                .result(savedProfile)
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/admin/drivers/{driverId}/unblock")
    public ApiResponse<DriverProfile> unblockDriver(@PathVariable UUID driverId) {
        DriverProfile profile = driverProfileRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver profile not found"));

        profile.setAccountStatus(AccountLifecycleStatus.ACTIVE);
        DriverProfile savedProfile = driverProfileRepository.save(profile);

        try {
            authAccountSyncClient.syncAccountLifecycle(savedProfile);
        } catch (Exception e) {
            log.error("Failed to sync unblock to auth-service for driver {}: {}", driverId, e.getMessage());
        }

        return ApiResponse.<DriverProfile>builder()
                .message("Driver unblocked successfully")
                .result(savedProfile)
                .build();
    }

    @PostMapping("/internal/drivers")
    public ApiResponse<DriverProfile> createInternalDriver(@RequestBody Map<String, String> body) {
        String externalUserId = body.get("userId");
        String fullName = body.get("fullName");
        String email = body.get("email");
        String phoneNumber = body.get("phoneNumber");
        String avatarUrl = body.get("avatarUrl");

        DriverProfile profile = driverProfileRepository.findByExternalUserId(externalUserId)
                .orElseGet(() -> {
                    DriverProfile created = new DriverProfile();
                    created.setExternalUserId(externalUserId);
                    created.setVerificationStatus(DriverVerificationStatus.PENDING);
                    return created;
                });

        profile.setFullName(fullName);
        profile.setEmail(email);
        profile.setPhoneNumber(phoneNumber);
        profile.setAvatarUrl(avatarUrl);

        DriverProfile savedProfile = driverProfileRepository.save(profile);

        return ApiResponse.<DriverProfile>builder()
                .message("Synchronized driver profile successfully")
                .result(savedProfile)
                .build();
    }
}
