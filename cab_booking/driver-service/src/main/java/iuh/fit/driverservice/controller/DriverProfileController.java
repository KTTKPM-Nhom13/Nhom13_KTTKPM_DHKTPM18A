package iuh.fit.driverservice.controller;

import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.driverservice.dto.request.HandleDriverAssignmentRequest;
import iuh.fit.driverservice.dto.request.UpdateDriverAvailabilityRequest;
import iuh.fit.driverservice.dto.request.UpsertDriverProfileRequest;
import iuh.fit.driverservice.dto.response.DriverAvailabilityResponse;
import iuh.fit.driverservice.dto.response.DriverCurrentRideResponse;
import iuh.fit.driverservice.dto.response.DriverEarningsSummaryResponse;
import iuh.fit.driverservice.dto.response.DriverProfileResponse;
import iuh.fit.driverservice.dto.response.DriverRevenueStatsResponse;
import iuh.fit.driverservice.security.CurrentUserFacade;
import iuh.fit.driverservice.service.DriverProfileService;
import iuh.fit.driverservice.service.DriverRideCommandService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/drivers/me")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DriverProfileController {
    DriverProfileService driverProfileService;
    DriverRideCommandService driverRideCommandService;
    CurrentUserFacade currentUserFacade;

    @GetMapping("/profile")
    public ApiResponse<DriverProfileResponse> getProfile() {
        return ApiResponse.<DriverProfileResponse>builder()
                .message("Fetched driver profile successfully")
                .result(driverProfileService.getProfile(currentUserFacade.getCurrentUserId()))
                .build();
    }

    @GetMapping("/revenue/stats")
    public ApiResponse<DriverRevenueStatsResponse> getRevenueStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        if (start == null) start = LocalDateTime.now().minusMonths(1);
        if (end == null) end = LocalDateTime.now();

        return ApiResponse.<DriverRevenueStatsResponse>builder()
                .message("Fetched driver revenue stats successfully")
                .result(driverProfileService.getRevenueStats(currentUserFacade.getCurrentUserId(), start, end))
                .build();
    }

    @PutMapping("/profile")
    public ApiResponse<DriverProfileResponse> upsertProfile(@Valid @RequestBody UpsertDriverProfileRequest request) {
        return ApiResponse.<DriverProfileResponse>builder()
                .message("Saved driver profile successfully")
                .result(driverProfileService.upsertProfile(currentUserFacade.getCurrentUserId(), request))
                .build();
    }

    @PatchMapping("/availability")
    public ApiResponse<DriverAvailabilityResponse> updateAvailability(
            @Valid @RequestBody UpdateDriverAvailabilityRequest request) {
        return ApiResponse.<DriverAvailabilityResponse>builder()
                .message("Updated driver availability successfully")
                .result(driverProfileService.updateAvailability(currentUserFacade.getCurrentUserId(), request))
                .build();
    }

    @GetMapping("/current-ride")
    public ApiResponse<DriverCurrentRideResponse> getCurrentRide() {
        return ApiResponse.<DriverCurrentRideResponse>builder()
                .message("Fetched current ride successfully")
                .result(driverProfileService.getCurrentRide(currentUserFacade.getCurrentUserId()))
                .build();
    }

    @PostMapping("/rides/assignment")
    public ApiResponse<DriverCurrentRideResponse> handleAssignment(
            @Valid @RequestBody HandleDriverAssignmentRequest request) {
        return ApiResponse.<DriverCurrentRideResponse>builder()
                .message("Handled driver assignment successfully")
                .result(driverRideCommandService.handleAssignment(currentUserFacade.getCurrentUserId(), request))
                .build();
    }

    @PostMapping("/rides/{rideId}/accept")
    public ApiResponse<DriverCurrentRideResponse> acceptRide(@PathVariable String rideId) {
        return ApiResponse.<DriverCurrentRideResponse>builder()
                .message("Ride accept request submitted")
                .result(driverRideCommandService.acceptRide(currentUserFacade.getCurrentUserId(), rideId))
                .build();
    }

    @PostMapping("/rides/{rideId}/reject")
    public ApiResponse<DriverCurrentRideResponse> rejectRide(@PathVariable String rideId) {
        return ApiResponse.<DriverCurrentRideResponse>builder()
                .message("Ride reject request submitted")
                .result(driverRideCommandService.rejectRide(currentUserFacade.getCurrentUserId(), rideId))
                .build();
    }

    @GetMapping("/earnings/summary")
    public ApiResponse<DriverEarningsSummaryResponse> getEarningsSummary() {
        return ApiResponse.<DriverEarningsSummaryResponse>builder()
                .message("Fetched driver earnings summary successfully")
                .result(driverProfileService.getEarningsSummary(currentUserFacade.getCurrentUserId()))
                .build();
    }
}
