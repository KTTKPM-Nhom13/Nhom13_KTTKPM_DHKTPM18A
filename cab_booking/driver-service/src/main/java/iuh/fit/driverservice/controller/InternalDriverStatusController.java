package iuh.fit.driverservice.controller;

import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.driverservice.dto.response.DriverStatusCheckResponse;
import iuh.fit.driverservice.service.DriverProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/drivers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalDriverStatusController {
    DriverProfileService driverProfileService;

    @GetMapping("/{driverId}/availability")
    public ApiResponse<DriverStatusCheckResponse> checkAvailability(@PathVariable String driverId) {
        return ApiResponse.<DriverStatusCheckResponse>builder()
                .message("Checked driver availability successfully")
                .result(driverProfileService.checkAvailability(driverId))
                .build();
    }
}
