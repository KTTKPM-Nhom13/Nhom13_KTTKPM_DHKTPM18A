package iuh.fit.driverservice.controller;

import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.driverservice.dto.response.DriverProfileResponse;
import iuh.fit.driverservice.service.DriverProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PublicDriverProfileController {
    DriverProfileService driverProfileService;

    @GetMapping("/{driverId}/profile")
    public ApiResponse<DriverProfileResponse> getDriverProfile(@PathVariable String driverId) {
        return ApiResponse.<DriverProfileResponse>builder()
                .message("Fetched driver profile successfully")
                .result(driverProfileService.getProfile(driverId))
                .build();
    }
}
