package iuh.fit.userservice.controller;

import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.userservice.dto.request.RegisterUserDeviceRequest;
import iuh.fit.userservice.dto.request.UpdateUserDeviceSessionRequest;
import iuh.fit.userservice.dto.response.UserDeviceResponse;
import iuh.fit.userservice.security.CurrentUserFacade;
import iuh.fit.userservice.service.UserDeviceService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me/devices")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserDeviceController {
    UserDeviceService userDeviceService;
    CurrentUserFacade currentUserFacade;

    @GetMapping
    public ApiResponse<List<UserDeviceResponse>> getMyDevices() {
        return ApiResponse.<List<UserDeviceResponse>>builder()
                .message("Fetched user devices successfully")
                .result(userDeviceService.getMyDevices(currentUserFacade.getCurrentUserId()))
                .build();
    }

    @PostMapping
    public ApiResponse<UserDeviceResponse> registerDevice(@Valid @RequestBody RegisterUserDeviceRequest request) {
        return ApiResponse.<UserDeviceResponse>builder()
                .message("Registered user device successfully")
                .result(userDeviceService.registerDevice(currentUserFacade.getCurrentUserId(), request))
                .build();
    }

    @PatchMapping("/{deviceId}")
    public ApiResponse<UserDeviceResponse> updateDevice(
            @PathVariable UUID deviceId,
            @Valid @RequestBody UpdateUserDeviceSessionRequest request) {
        return ApiResponse.<UserDeviceResponse>builder()
                .message("Updated user device successfully")
                .result(userDeviceService.updateDevice(currentUserFacade.getCurrentUserId(), deviceId, request))
                .build();
    }

    @PostMapping("/{deviceId}/heartbeat")
    public ApiResponse<UserDeviceResponse> heartbeat(@PathVariable UUID deviceId) {
        return ApiResponse.<UserDeviceResponse>builder()
                .message("Device heartbeat updated successfully")
                .result(userDeviceService.touchDevice(currentUserFacade.getCurrentUserId(), deviceId))
                .build();
    }

    @PostMapping("/{deviceId}/revoke")
    public ApiResponse<UserDeviceResponse> revoke(@PathVariable UUID deviceId) {
        return ApiResponse.<UserDeviceResponse>builder()
                .message("Device session revoked successfully")
                .result(userDeviceService.revokeDevice(currentUserFacade.getCurrentUserId(), deviceId))
                .build();
    }
}
